package uz.footballai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.config.MlServiceConfig;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class MlServiceClient {

    private final MlServiceConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MlServiceClient(
            MlServiceConfig config,
            @org.springframework.beans.factory.annotation.Qualifier("mlServiceHttpClient") OkHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    /**
     * ML servisga video tahlilni boshlash uchun so'rov yuborish.
     */
    public String startAnalysis(UUID matchId, String videoMinioKey, String callbackUrl) {
        return startAnalysis(matchId, videoMinioKey, callbackUrl, null, null);
    }

    public String startAnalysis(
            UUID matchId,
            String videoMinioKey,
            String callbackUrl,
            java.util.Map<String, java.util.List<Integer>> calibrationPoints
    ) {
        return startAnalysis(matchId, videoMinioKey, callbackUrl, calibrationPoints, null);
    }

    /**
     * Calibration + long-form bayrog'i bilan tahlil boshlash.
     *
     * @param isLongForm null = avtomatik (video uzunligi asosida)
     *                   true = majburlash 90-min mode
     *                   false = qisqa video mode
     */
    public String startAnalysis(
            UUID matchId,
            String videoMinioKey,
            String callbackUrl,
            java.util.Map<String, java.util.List<Integer>> calibrationPoints,
            Boolean isLongForm
    ) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("match_id", matchId.toString());
            body.put("video_minio_key", videoMinioKey);
            if (callbackUrl != null) {
                body.put("callback_url", callbackUrl);
            }
            if (calibrationPoints != null && !calibrationPoints.isEmpty()) {
                body.set("calibration_points", objectMapper.valueToTree(calibrationPoints));
                log.info("Qo'lda kalibrlash nuqtalari: {} ta", calibrationPoints.size());
            }
            if (isLongForm != null) {
                body.put("is_long_form", isLongForm);
                log.info("Long-form mode: {}", isLongForm);
            }

            String url = config.getMlServiceUrl() + "/analyze";
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + config.getInternalToken())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_MEDIA))
                    .build();

            log.info("ML servisga so'rov: match={}", matchId);

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("ML servis xatosi: {} - {}", response.code(), errorBody);
                    throw new BusinessException("ML servis xatosi: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode json = objectMapper.readTree(responseBody);
                String jobId = json.get("job_id").asText();

                log.info("ML job yaratildi: {}", jobId);
                return jobId;
            }

        } catch (IOException e) {
            log.error("ML servis bilan bog'lanishda xato:", e);
            throw new BusinessException("ML servis ishlamayapti: " + e.getMessage());
        }
    }

    /**
     * Crash bo'lgan jobni qaytadan boshlash (checkpoint'dan).
     */
    public String resumeAnalysis(
            String mlJobId,
            UUID matchId,
            String videoMinioKey,
            String callbackUrl,
            java.util.Map<String, java.util.List<Integer>> calibrationPoints
    ) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("match_id", matchId.toString());
            body.put("video_minio_key", videoMinioKey);
            body.put("resume_from_checkpoint", true);
            if (callbackUrl != null) body.put("callback_url", callbackUrl);
            if (calibrationPoints != null && !calibrationPoints.isEmpty()) {
                body.set("calibration_points", objectMapper.valueToTree(calibrationPoints));
            }

            String url = config.getMlServiceUrl() + "/analyze/resume/" + mlJobId;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + config.getInternalToken())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new BusinessException("Resume xatosi: " + response.code() + " - " + errorBody);
                }
                JsonNode json = objectMapper.readTree(response.body().string());
                String jobId = json.get("job_id").asText();
                log.info("ML job resume qilindi: {}", jobId);
                return jobId;
            }
        } catch (IOException e) {
            throw new BusinessException("Resume da xato: " + e.getMessage());
        }
    }

    /**
     * Jobni bekor qilish.
     */
    public boolean cancelJob(String mlJobId) {
        try {
            String url = config.getMlServiceUrl() + "/jobs/" + mlJobId;
            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .addHeader("Authorization", "Bearer " + config.getInternalToken())
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.warn("Cancel xatosi: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ML servisdan job statusini olish.
     */
    public JsonNode getJobStatus(String mlJobId) {
        try {
            String url = config.getMlServiceUrl() + "/jobs/" + mlJobId;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BusinessException("ML status olishda xato: " + response.code());
                }
                return objectMapper.readTree(response.body().string());
            }
        } catch (IOException e) {
            throw new BusinessException("ML servis bilan bog'lanib bo'lmadi: " + e.getMessage());
        }
    }

    /**
     * Tugagan job natijasini olish.
     */
    public JsonNode getJobResult(String mlJobId) {
        try {
            String url = config.getMlServiceUrl() + "/jobs/" + mlJobId + "/result";
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 425) {
                    throw new BusinessException("Job hali tayyor emas");
                }
                if (!response.isSuccessful()) {
                    throw new BusinessException("Natija olishda xato: " + response.code());
                }
                return objectMapper.readTree(response.body().string());
            }
        } catch (IOException e) {
            throw new BusinessException("ML servis bilan bog'lanib bo'lmadi: " + e.getMessage());
        }
    }

    /**
     * ML servis health check.
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                    .url(config.getMlServiceUrl() + "/health")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.warn("ML servis health check muvaffaqiyatsiz: {}", e.getMessage());
            return false;
        }
    }
}
