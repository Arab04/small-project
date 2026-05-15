package uz.footballai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.match.*;
import uz.footballai.match.dto.MatchEventRequest;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisService {

    private final VideoAnalysisJobRepository jobRepository;
    private final MlServiceClient mlServiceClient;
    private final MatchService matchService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final uz.footballai.runpod.RunPodGpuManager runPodGpuManager;

    @Value("${app.ml-callback-url:http://backend:8060/api/internal/ml-callback}")
    private String mlCallbackUrl;

    /**
     * Video tahlilni boshlash.
     */
    @Transactional
    public VideoAnalysisJob startAnalysis(UUID matchId) {
        return startAnalysis(matchId, null);
    }

    /**
     * Calibration nuqtalari bilan boshlash.
     * Murabbiy UI'da maydonning 4-6 ta kalit nuqtasini bosib ko'rsatadi.
     */
    @Transactional
    public VideoAnalysisJob startAnalysis(
            UUID matchId,
            java.util.Map<String, java.util.List<Integer>> calibrationPoints
    ) {
        User user = userService.getCurrentUser();
        Match match = matchService.findByIdAndClub(matchId, user.getClub().getId());

        if (match.getVideoUrl() == null) {
            throw new BusinessException("Bu o'yinga avval video yuklang");
        }

        // RunPod GPU pod uxlagan bo'lsa - ishga tushirib, /health tayyor bo'lishini kutamiz
        runPodGpuManager.ensureRunning();
        if (!mlServiceClient.isHealthy()) {
            throw new BusinessException("ML servis hozir ishlamayapti. Keyinroq urinib ko'ring.");
        }

        VideoAnalysisJob job = VideoAnalysisJob.builder()
                .match(match)
                .videoMinioKey(match.getVideoUrl())
                .status(VideoJobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .progress(0)
                .build();
        job = jobRepository.save(job);

        try {
            String callbackUrl = mlCallbackUrl;
            String mlJobId = mlServiceClient.startAnalysis(
                    matchId, match.getVideoUrl(), callbackUrl, calibrationPoints
            );

            job.setMlJobId(mlJobId);
            job.setStatus(VideoJobStatus.DOWNLOADING);
            jobRepository.save(job);

            log.info("Video tahlil boshlandi: match={}, job={}, calibration={}",
                    matchId, mlJobId, calibrationPoints != null ? "manual" : "auto");
            return job;

        } catch (Exception e) {
            job.setStatus(VideoJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
            throw e;
        }
    }

    /**
     * Job statusini ML servisdan yangilash.
     */
    @Transactional
    public VideoAnalysisJob refreshJobStatus(UUID jobId) {
        VideoAnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId));

        if (job.getMlJobId() == null) {
            return job;
        }

        if (job.getStatus() == VideoJobStatus.COMPLETED || job.getStatus() == VideoJobStatus.FAILED) {
            return job; // Yakunlangan
        }

        try {
            JsonNode statusJson = mlServiceClient.getJobStatus(job.getMlJobId());

            String status = statusJson.get("status").asText();
            int progress = statusJson.has("progress") ? statusJson.get("progress").asInt() : 0;

            job.setStatus(VideoJobStatus.valueOf(status));
            job.setProgress(progress);
            job.setCurrentStep(statusJson.has("current_step") ? statusJson.get("current_step").asText() : "");

            // Agar tugagan bo'lsa - natijani olib, eventlarni saqlash
            if ("COMPLETED".equals(status)) {
                processCompletedJob(job);
            }

            jobRepository.save(job);
            return job;

        } catch (Exception e) {
            log.error("Job status yangilashda xato:", e);
            return job;
        }
    }

    /**
     * Tugagan job natijasini qayta ishlash va eventlarni saqlash.
     */
    @Transactional
    protected void processCompletedJob(VideoAnalysisJob job) {
        try {
            JsonNode result = mlServiceClient.getJobResult(job.getMlJobId());

            // Raw natijani saqlash
            job.setRawResultJson(objectMapper.writeValueAsString(result));
            job.setCompletedAt(LocalDateTime.now());

            // Event'larni MatchEvent sifatida saqlash
            JsonNode eventsArray = result.get("events");
            if (eventsArray != null && eventsArray.isArray()) {
                List<MatchEventRequest> eventRequests = new ArrayList<>();

                for (JsonNode eventNode : eventsArray) {
                    MatchEventRequest req = MatchEventRequest.builder()
                            .minute(eventNode.get("minute").asInt())
                            .type(MatchEventType.valueOf(eventNode.get("event_type").asText()))
                            .teamSide(eventNode.has("team_side") && !eventNode.get("team_side").isNull()
                                    ? TeamSide.valueOf(eventNode.get("team_side").asText())
                                    : TeamSide.HOME)
                            .description(eventNode.has("description")
                                    ? eventNode.get("description").asText()
                                    : "AI tomonidan aniqlangan")
                            .playerName(eventNode.has("player_name") && !eventNode.get("player_name").isNull()
                                    ? eventNode.get("player_name").asText()
                                    : null)
                            .build();
                    eventRequests.add(req);
                }

                if (!eventRequests.isEmpty()) {
                    matchService.addEventsForMatch(job.getMatch().getId(), eventRequests);
                    log.info("✓ {} ta event saqlandi (match={})",
                            eventRequests.size(), job.getMatch().getId());
                }

                job.setEventsDetected(eventRequests.size());
            }

            // Statistika
            if (result.has("total_frames_processed")) {
                job.setFramesProcessed(result.get("total_frames_processed").asInt());
            }
            if (result.has("confidence_average")) {
                job.setAverageConfidence(result.get("confidence_average").asDouble());
            }

            // ===== YANGI: Long-form metadata =====
            if (result.has("is_long_form")) {
                job.setIsLongForm(result.get("is_long_form").asBoolean());
            }
            if (result.has("match_phases") && result.get("match_phases").isArray()) {
                job.setPhasesDetected(result.get("match_phases").size());
            }
            if (result.has("timeline") && result.get("timeline").isArray()) {
                job.setTimelineWindowsCount(result.get("timeline").size());
            }
            if (result.has("stamina_analysis") && !result.get("stamina_analysis").isNull()) {
                JsonNode stamina = result.get("stamina_analysis");
                if (stamina.has("home_fatigue_detected")) {
                    job.setHomeStaminaDetected(stamina.get("home_fatigue_detected").asBoolean());
                }
                if (stamina.has("away_fatigue_detected")) {
                    job.setAwayStaminaDetected(stamina.get("away_fatigue_detected").asBoolean());
                }
            }

            // Hisob (agar topilgan bo'lsa)
            JsonNode finalScore = result.get("final_score");
            if (finalScore != null && !finalScore.isNull()) {
                Match match = job.getMatch();
                if (match.getOurScore() == null && match.getOpponentScore() == null) {
                    int home = finalScore.get("home_score").asInt();
                    int away = finalScore.get("away_score").asInt();
                    if (match.isHome()) {
                        match.setOurScore(home);
                        match.setOpponentScore(away);
                    } else {
                        match.setOurScore(away);
                        match.setOpponentScore(home);
                    }
                    match.setStatus(MatchStatus.FINISHED);
                    log.info("✓ Hisob avtomatik saqlandi: {}:{}", home, away);
                }
            }

        } catch (Exception e) {
            log.error("Tugagan job ni qayta ishlashda xato:", e);
            job.setErrorMessage("Natija qayta ishlanmadi: " + e.getMessage());
        }
    }

    /**
     * Match uchun barcha video tahlillar.
     */
    public List<VideoAnalysisJob> getJobsForMatch(UUID matchId) {
        User user = userService.getCurrentUser();
        matchService.findByIdAndClub(matchId, user.getClub().getId());
        return jobRepository.findByMatchIdAndDeletedFalseOrderByCreatedAtDesc(matchId);
    }

    public VideoAnalysisJob findById(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("VideoAnalysisJob", "id", jobId));
    }

    // ============== 90-min YANGI METODLAR ==============

    /**
     * 90 daqiqalik to'liq match tahlilini majburlash.
     * is_long_form=true bilan ML servisga yuboriladi - timeline + period + stamina hisoblanadi.
     */
    @Transactional
    public VideoAnalysisJob startLongFormAnalysis(
            UUID matchId,
            java.util.Map<String, java.util.List<Integer>> calibrationPoints
    ) {
        User user = userService.getCurrentUser();
        Match match = matchService.findByIdAndClub(matchId, user.getClub().getId());

        if (match.getVideoUrl() == null) {
            throw new BusinessException("Bu o'yinga avval video yuklang");
        }
        // RunPod GPU pod uxlagan bo'lsa - ishga tushirib, /health tayyor bo'lishini kutamiz
        runPodGpuManager.ensureRunning();
        if (!mlServiceClient.isHealthy()) {
            throw new BusinessException("ML servis hozir ishlamayapti. Keyinroq urinib ko'ring.");
        }

        VideoAnalysisJob job = VideoAnalysisJob.builder()
                .match(match)
                .videoMinioKey(match.getVideoUrl())
                .status(VideoJobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .progress(0)
                .isLongForm(true)
                .build();
        job = jobRepository.save(job);

        try {
            String callbackUrl = mlCallbackUrl;
            String mlJobId = mlServiceClient.startAnalysis(
                    matchId, match.getVideoUrl(), callbackUrl, calibrationPoints,
                    /* isLongForm= */ true
            );

            job.setMlJobId(mlJobId);
            job.setStatus(VideoJobStatus.DOWNLOADING);
            jobRepository.save(job);

            log.info("Long-form (90-min) tahlil boshlandi: match={}, job={}",
                    matchId, mlJobId);
            return job;

        } catch (Exception e) {
            job.setStatus(VideoJobStatus.FAILED);
            job.setErrorMessage("Long-form ML servisga ulanmadi: " + e.getMessage());
            jobRepository.save(job);
            throw new BusinessException("Long-form tahlil boshlanmadi: " + e.getMessage());
        }
    }

    /**
     * Crash bo'lgan jobni qaytadan boshlash (oxirgi checkpoint'dan).
     */
    @Transactional
    public VideoAnalysisJob resumeJob(UUID jobId) {
        VideoAnalysisJob job = findById(jobId);
        User user = userService.getCurrentUser();
        // Ownership check
        matchService.findByIdAndClub(job.getMatch().getId(), user.getClub().getId());

        if (job.getMlJobId() == null) {
            throw new BusinessException("Bu jobning ML ID si yo'q - resume mumkin emas");
        }
        if (job.getStatus() == VideoJobStatus.COMPLETED) {
            throw new BusinessException("Job allaqachon tugagan");
        }
        // RunPod GPU pod uxlagan bo'lsa - ishga tushiramiz
        runPodGpuManager.ensureRunning();
        if (!mlServiceClient.isHealthy()) {
            throw new BusinessException("ML servis hozir ishlamayapti");
        }

        try {
            String callbackUrl = mlCallbackUrl;
            // Resume: yangi mlJobId emas, eski ID ishlatiladi
            String resumedMlJobId = mlServiceClient.resumeAnalysis(
                    job.getMlJobId(),
                    job.getMatch().getId(),
                    job.getVideoMinioKey(),
                    callbackUrl,
                    null  // calibration allaqachon checkpoint'da
            );

            job.setMlJobId(resumedMlJobId);
            job.setStatus(VideoJobStatus.DOWNLOADING);
            job.setErrorMessage(null);
            jobRepository.save(job);

            log.info("Job resume qilindi: jobId={}, mlJobId={}", jobId, resumedMlJobId);
            return job;

        } catch (Exception e) {
            log.error("Resume xato:", e);
            throw new BusinessException("Resume xatosi: " + e.getMessage());
        }
    }

    /**
     * Jobni bekor qilish.
     */
    @Transactional
    public boolean cancelJob(UUID jobId) {
        VideoAnalysisJob job = findById(jobId);
        User user = userService.getCurrentUser();
        matchService.findByIdAndClub(job.getMatch().getId(), user.getClub().getId());

        boolean ok = false;
        if (job.getMlJobId() != null) {
            ok = mlServiceClient.cancelJob(job.getMlJobId());
        }

        job.setStatus(VideoJobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        log.info("Job bekor qilindi: jobId={}, mlOk={}", jobId, ok);
        return true;
    }

    /**
     * Yakuniy raw natija JSON ko'rinishida (timeline, periods, stamina, heatmap keys).
     * Frontend bu JSON ni parse qilib momentum chart, period bar, stamina chart chizadi.
     */
    public String getRawResult(UUID jobId) {
        VideoAnalysisJob job = findById(jobId);
        User user = userService.getCurrentUser();
        matchService.findByIdAndClub(job.getMatch().getId(), user.getClub().getId());

        if (job.getStatus() != VideoJobStatus.COMPLETED) {
            throw new BusinessException("Tahlil hali tugamagan. Hozirgi status: " + job.getStatus());
        }
        if (job.getRawResultJson() == null) {
            throw new BusinessException("Natija JSON topilmadi");
        }
        return job.getRawResultJson();
    }
}
