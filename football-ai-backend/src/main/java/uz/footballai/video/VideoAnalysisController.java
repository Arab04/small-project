package uz.footballai.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Video Analysis REST API.
 *
 * 90 daqiqalik video uchun yangi endpointlar:
 *  - /start-long-form  (90 min mode'ni majburlash)
 *  - /jobs/{id}/resume (crash bo'lsa qaytadan)
 *  - /jobs/{id}/cancel (foydalanuvchi to'xtatish)
 *
 * Status'da yangi maydonlar:
 *  - currentMinute, totalMinutes (real-time progress)
 *  - currentPhase (FIRST_HALF/HALFTIME/SECOND_HALF)
 *  - etaSeconds (taxminiy qolgan vaqt)
 *  - phasesDetected, timelineWindowsCount, staminaDetected (tugagandan keyin)
 */
@RestController
@RequestMapping("/matches/{matchId}/analysis")
@RequiredArgsConstructor
@Tag(name = "Video Analysis", description = "AI bilan video tahlil (ML servis)")
public class VideoAnalysisController {

    private final VideoAnalysisService videoAnalysisService;

    @PostMapping("/start")
    @Operation(
        summary = "Avtomatik tahlilni boshlash",
        description = "Avtomatik kalibrlash bilan. " +
                      "90 daqiqadan uzun videolar avtomatik long-form mode'ga o'tadi."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAnalysis(
            @PathVariable UUID matchId
    ) {
        VideoAnalysisJob job = videoAnalysisService.startAnalysis(matchId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(buildStartResponse(job, "Auto"),
                        "Tahlil so'rovi qabul qilindi"));
    }

    @PostMapping("/start-with-calibration")
    @Operation(
        summary = "Calibration nuqtalari bilan tahlil boshlash",
        description = "Murabbiy birinchi freymdan 4-6 ta kalit nuqta bosib ko'rsatadi."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAnalysisWithCalibration(
            @PathVariable UUID matchId,
            @RequestBody Map<String, List<Integer>> calibrationPoints
    ) {
        VideoAnalysisJob job = videoAnalysisService.startAnalysis(matchId, calibrationPoints);
        Map<String, Object> response = buildStartResponse(job, "Manual calibration");
        response.put("calibrationPoints", calibrationPoints.size());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response, "Calibration qabul qilindi"));
    }

    @PostMapping("/start-long-form")
    @Operation(
        summary = "90 daqiqalik to'liq match tahlilini majburlash",
        description = "Long-form mode majburlash (timeline + period comparison + stamina). " +
                      "Tahlil 25-40 daqiqa davom etadi."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> startLongFormAnalysis(
            @PathVariable UUID matchId,
            @RequestBody(required = false) Map<String, List<Integer>> calibrationPoints
    ) {
        VideoAnalysisJob job = videoAnalysisService.startLongFormAnalysis(matchId, calibrationPoints);
        Map<String, Object> response = buildStartResponse(job, "Long-form");
        response.put("estimatedDurationMinutes", 30);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response,
                        "90 daqiqalik tahlil boshlandi. Hisobotni 30-40 daqiqada oling."));
    }

    @PostMapping("/jobs/{jobId}/resume")
    @Operation(
        summary = "Crash bo'lgan jobni qaytadan boshlash",
        description = "Server crash bo'lsa, oxirgi checkpoint'dan davom etadi."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumeJob(
            @PathVariable UUID matchId,
            @PathVariable UUID jobId
    ) {
        VideoAnalysisJob job = videoAnalysisService.resumeJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(
                buildStartResponse(job, "Resumed"),
                "Tahlil qaytadan boshlandi"));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Davom etayotgan jobni bekor qilish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelJob(
            @PathVariable UUID matchId,
            @PathVariable UUID jobId
    ) {
        boolean ok = videoAnalysisService.cancelJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("jobId", jobId, "cancelled", ok),
                ok ? "Job bekor qilindi" : "Bekor qilib bo'lmadi"));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Tahlil holatini ko'rish (real-time progress)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJobStatus(
            @PathVariable UUID matchId,
            @PathVariable UUID jobId
    ) {
        VideoAnalysisJob job = videoAnalysisService.refreshJobStatus(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("id", job.getId());
        response.put("status", job.getStatus().name());
        response.put("progress", job.getProgress() != null ? job.getProgress() : 0);
        response.put("currentStep", job.getCurrentStep() != null ? job.getCurrentStep() : "");
        response.put("eventsDetected", job.getEventsDetected() != null ? job.getEventsDetected() : 0);
        response.put("framesProcessed", job.getFramesProcessed() != null ? job.getFramesProcessed() : 0);
        response.put("errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "");

        // 90-min progress
        if (job.getCurrentMinute() != null) {
            response.put("currentMinute", job.getCurrentMinute());
        }
        if (job.getTotalMinutes() != null) {
            response.put("totalMinutes", job.getTotalMinutes());
        }
        if (job.getCurrentPhase() != null) {
            response.put("currentPhase", job.getCurrentPhase());
        }
        if (job.getEtaSeconds() != null) {
            response.put("etaSeconds", job.getEtaSeconds());
        }
        if (Boolean.TRUE.equals(job.getIsLongForm())) {
            response.put("isLongForm", true);
        }

        // Tugagan bo'lsa - long-form yakuniy meta
        if (job.getStatus() == VideoJobStatus.COMPLETED) {
            if (job.getPhasesDetected() != null) {
                response.put("phasesDetected", job.getPhasesDetected());
            }
            if (job.getTimelineWindowsCount() != null) {
                response.put("timelineWindowsCount", job.getTimelineWindowsCount());
            }
            response.put("homeStaminaDetected",
                    Boolean.TRUE.equals(job.getHomeStaminaDetected()));
            response.put("awayStaminaDetected",
                    Boolean.TRUE.equals(job.getAwayStaminaDetected()));
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/jobs")
    @Operation(summary = "Match'ning barcha tahlil tarixi")
    public ResponseEntity<ApiResponse<List<VideoAnalysisJob>>> getJobs(
            @PathVariable UUID matchId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(videoAnalysisService.getJobsForMatch(matchId)));
    }

    @GetMapping("/jobs/{jobId}/result")
    @Operation(summary = "Tugagan tahlilning to'liq natijasi (timeline, periods, stamina, heatmap)")
    public ResponseEntity<ApiResponse<String>> getJobResult(
            @PathVariable UUID matchId,
            @PathVariable UUID jobId
    ) {
        String rawJson = videoAnalysisService.getRawResult(jobId);
        return ResponseEntity.ok(ApiResponse.ok(rawJson));
    }

    // ============== HELPERS ==============

    private Map<String, Object> buildStartResponse(VideoAnalysisJob job, String mode) {
        Map<String, Object> r = new HashMap<>();
        r.put("jobId", job.getId());
        r.put("mlJobId", job.getMlJobId() != null ? job.getMlJobId() : "");
        r.put("status", job.getStatus().name());
        r.put("mode", mode);
        r.put("message", "Video tahlil boshlandi.");
        return r;
    }
}
