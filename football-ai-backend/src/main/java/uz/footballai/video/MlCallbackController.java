package uz.footballai.video;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;

import java.time.LocalDateTime;

/**
 * ML servis tomonidan progress xabarlar uchun internal endpoint.
 *
 * 90-min video uchun yangiliklar:
 *  - current_minute, total_minutes - hozirgi daqiqa
 *  - current_phase - hozirgi yarim
 *  - eta_seconds - taxminiy qolgan vaqt
 *  - last_checkpoint_frame - resume uchun
 *
 * Authentication: Bearer token (production'da JWT yoki shared secret).
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class MlCallbackController {

    private final VideoAnalysisJobRepository jobRepository;
    private final MlServiceClient mlServiceClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final uz.footballai.runpod.RunPodGpuManager runPodGpuManager;

    @Value("${ml-service.internal-token:internal-secret-token}")
    private String expectedToken;

    @PostMapping("/ml-callback")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> handleCallback(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Token validatsiya
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Invalid authorization header in ML callback");
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Unauthorized"));
        }
        String token = authHeader.substring(7);
        if (!expectedToken.equals(token)) {
            log.warn("Invalid token in ML callback");
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Invalid token"));
        }

        log.debug("ML callback keldi: {}", payload);

        // RunPod faolligini yangilash — pod hali ishlayotganini bildirish
        runPodGpuManager.markActivity();

        try {
            String mlJobId = payload.get("job_id").asText();
            String status = payload.get("status").asText();
            int progress = payload.has("progress") ? payload.get("progress").asInt() : 0;

            jobRepository.findByMlJobId(mlJobId).ifPresent(job -> {
                // Asosiy maydonlar
                try {
                    job.setStatus(VideoJobStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    log.warn("Noma'lum status: {}", status);
                }
                job.setProgress(progress);

                // 90-min progress maydonlari
                if (payload.has("current_minute")) {
                    job.setCurrentMinute(payload.get("current_minute").asDouble());
                }
                if (payload.has("total_minutes")) {
                    job.setTotalMinutes(payload.get("total_minutes").asDouble());
                }
                if (payload.has("current_phase")) {
                    job.setCurrentPhase(payload.get("current_phase").asText());
                }
                if (payload.has("eta_seconds")) {
                    job.setEtaSeconds(payload.get("eta_seconds").asInt());
                }
                if (payload.has("last_checkpoint_frame")) {
                    job.setLastCheckpointFrame(payload.get("last_checkpoint_frame").asInt());
                }
                if (payload.has("events_count")) {
                    job.setEventsDetected(payload.get("events_count").asInt());
                }

                // Tugashi
                if ("COMPLETED".equals(status)) {
                    job.setCompletedAt(LocalDateTime.now());
                    job.setProgress(100);
                    // ML servisdan natija JSON ni yuklab olish
                    try {
                        JsonNode resultJson = mlServiceClient.getJobResult(mlJobId);
                        job.setRawResultJson(objectMapper.writeValueAsString(resultJson));
                        log.info("Job natijasi saqlandi: {}", mlJobId);
                    } catch (Exception ex) {
                        log.error("Job natijasini olishda xato: {} - {}", mlJobId, ex.getMessage());
                    }
                } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                    job.setCompletedAt(LocalDateTime.now());
                }

                jobRepository.save(job);

                if (job.getCurrentMinute() != null && job.getTotalMinutes() != null) {
                    log.info("Job: {} → {} ({}%) | daqiqa {}/{} | ETA {}s",
                            mlJobId, status, progress,
                            job.getCurrentMinute(), job.getTotalMinutes(),
                            job.getEtaSeconds());
                } else {
                    log.info("Job yangilandi: {} → {} ({}%)", mlJobId, status, progress);
                }
            });

            return ResponseEntity.ok(ApiResponse.ok(null, "Callback qabul qilindi"));

        } catch (Exception e) {
            log.error("Callback ishlashda xato:", e);
            return ResponseEntity.ok(ApiResponse.error("Xato: " + e.getMessage()));
        }
    }
}
