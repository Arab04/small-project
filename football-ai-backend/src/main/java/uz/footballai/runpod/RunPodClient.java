package uz.footballai.runpod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * RunPod REST API client.
 *
 * Docs: https://rest.runpod.io/v1/docs
 *
 * Endpoints:
 *   POST /pods/{podId}/start  - uxlagan podni ishga tushirish
 *   POST /pods/{podId}/stop   - ishlayotgan podni to'xtatish
 *   GET  /pods/{podId}        - podning holatini olish
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunPodClient {

    private static final String BASE_URL = "https://rest.runpod.io/v1";

    @Value("${runpod.api-key:}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Pod holatini qaytaradi: RUNNING / EXITED / TERMINATED / ...
     */
    public PodStatus getStatus(String podId) {
        try {
            String body = client()
                    .get()
                    .uri("/pods/{id}", podId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode json = mapper.readTree(body);
            // Field nomlari: "desiredStatus", "lastStatusChange", "machine.podHostId", ...
            String desired = json.path("desiredStatus").asText("UNKNOWN");
            String runtime = json.path("runtime").path("uptimeInSeconds").asText("");
            return new PodStatus(desired, runtime);

        } catch (WebClientResponseException e) {
            log.error("RunPod getStatus xato [{}]: {} - {}", podId, e.getStatusCode(), e.getResponseBodyAsString());
            return new PodStatus("ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("RunPod getStatus xato [{}]: {}", podId, e.getMessage());
            return new PodStatus("ERROR", e.getMessage());
        }
    }

    /**
     * Podni ishga tushirish (cold start ~30-60 sek).
     * Bu chaqiruv darhol qaytadi — pod hali tayyor emas, /health bilan kutish kerak.
     */
    public boolean start(String podId) {
        try {
            client()
                    .post()
                    .uri("/pods/{id}/start", podId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            log.info("RunPod start so'raldi: {}", podId);
            return true;

        } catch (WebClientResponseException e) {
            log.error("RunPod start xato [{}]: {} - {}", podId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("RunPod start xato [{}]: {}", podId, e.getMessage());
            return false;
        }
    }

    /**
     * Podni to'xtatish — GPU uchun pul to'lash to'xtaydi (storage qoladi).
     */
    public boolean stop(String podId) {
        try {
            client()
                    .post()
                    .uri("/pods/{id}/stop", podId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            log.info("RunPod stop so'raldi: {}", podId);
            return true;

        } catch (WebClientResponseException e) {
            log.error("RunPod stop xato [{}]: {} - {}", podId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("RunPod stop xato [{}]: {}", podId, e.getMessage());
            return false;
        }
    }

    public record PodStatus(String desiredStatus, String runtime) {
        public boolean isRunning() {
            return "RUNNING".equalsIgnoreCase(desiredStatus);
        }
        public boolean isStopped() {
            return "EXITED".equalsIgnoreCase(desiredStatus)
                    || "TERMINATED".equalsIgnoreCase(desiredStatus);
        }
    }
}
