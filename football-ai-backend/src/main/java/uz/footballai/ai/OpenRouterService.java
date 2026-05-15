package uz.footballai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter AI servisi — futbol o'yin ma'lumotlarini Llama/Gemma modellari orqali tahlil qiladi.
 *
 * Diqqat:
 *  - API kalit application.yml dan o'qiladi (`openrouter.api-key`)
 *  - Avtomatik @PostConstruct chaqirilmaydi - bu method faqat aniq talab qilinganda chaqiriladi.
 *  - Retry policy ehtiyot bilan sozlangan (cheksiz emas).
 */
@Service
@Slf4j
public class OpenRouterService {

    private final PromptBuilder promptBuilder;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final String apiKey;
    private final String defaultModel;

    public OpenRouterService(PromptBuilder promptBuilder,
                             ResourceLoader resourceLoader,
                             ObjectMapper objectMapper,
                             WebClient.Builder webClientBuilder,
                             @Value("${openrouter.api-key:}") String apiKey,
                             @Value("${openrouter.model:meta-llama/llama-3.1-8b-instruct:free}") String defaultModel,
                             @Value("${openrouter.app-name:Football AI Analyzer}") String appName,
                             @Value("${openrouter.app-url:http://localhost:8060}") String appUrl) {
        this.promptBuilder = promptBuilder;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        // WebClient ni sozlaymiz
        WebClient.Builder builder = webClientBuilder
                .baseUrl("https://openrouter.ai/api/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", appUrl)
                .defaultHeader("X-Title", appName);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        this.webClient = builder.build();
    }

    /**
     * Test fayldan o'qib analiz qilish (faqat development uchun, qo'lda chaqiriladi).
     */
    public void analyzeJsonFromFileStream() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenRouter API kaliti sozlanmagan - analiz o'tkazib yuboriladi");
            return;
        }
        try {
            log.info("Fayl o'qilmoqda: test-007_result.json");
            Resource resource = resourceLoader.getResource("classpath:test-007_result.json");
            if (!resource.exists()) {
                log.warn("test-007_result.json topilmadi");
                return;
            }

            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            String compressedData = compressDataForAI(rootNode);

            log.info("Ma'lumot hajmi: {} ta belgi. OpenRouter'ga Stream yuborilmoqda...", compressedData.length());

            sendToOpenRouterStream(compressedData)
                    .doOnComplete(() -> log.info("--- Tahlil tugadi ---"))
                    .doOnError(error -> log.error("Stream xatosi: ", error))
                    .subscribe(chunk -> log.info(chunk));

        } catch (Exception e) {
            log.error("Faylni o'qishda xato: ", e);
        }
    }

    /**
     * JSON futbol ma'lumotlarni stream qilish public method.
     */
    public Flux<String> analyzeJsonStream(String jsonData) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new IllegalStateException("OpenRouter API kaliti sozlanmagan"));
        }
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            String compressedData = compressDataForAI(rootNode);
            return sendToOpenRouterStream(compressedData);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private String compressDataForAI(JsonNode rootNode) {
        StringBuilder summary = new StringBuilder();
        if (rootNode.has("match_id")) {
            summary.append("Match ID: ").append(rootNode.get("match_id").asText()).append("\n");
        }
        if (rootNode.has("video_info")) {
            JsonNode videoInfo = rootNode.get("video_info");
            if (videoInfo.has("duration_seconds")) {
                summary.append("Video davomiyligi: ")
                        .append(videoInfo.get("duration_seconds").asDouble())
                        .append(" sekund\n");
            }
            if (videoInfo.has("fps")) {
                summary.append("Kadrlar soni (FPS): ")
                        .append(videoInfo.get("fps").asDouble())
                        .append("\n");
            }
        }

        summary.append("\n--- O'YINCHILAR STATISTIKASI ---\n");
        if (rootNode.has("player_tracks")) {
            for (JsonNode player : rootNode.get("player_tracks")) {
                String trackId = player.has("track_id") ? player.get("track_id").asText() : "Noma'lum";
                String teamLabel = player.has("team_label") ? player.get("team_label").asText() : "Noma'lum";
                JsonNode positions = player.get("positions");
                int positionCount = (positions != null) ? positions.size() : 0;

                summary.append("- O'yinchi ID: ").append(trackId)
                        .append(" | Jamoa: ").append(teamLabel)
                        .append(" | Harakatlari soni: ").append(positionCount)
                        .append("\n");
            }
        }
        summary.append("\nUshbu futbol statistikasiga qisqa xulosa ber.");
        return summary.toString();
    }

    /**
     * WebClient orqali Flux (Stream) qaytaradigan metod.
     */
    private Flux<String> sendToOpenRouterStream(String summaryContent) {
        Map<String, Object> requestBody = Map.of(
                "model", defaultModel,
                "messages", List.of(
                        Map.of("role", "system", "content", promptBuilder.buildSystemPrompt()),
                        Map.of("role", "user", "content",
                                "Quyida futbol o'yinining qisqartirilgan statistikasi keltirilgan. Buni tahlil qil:\n\n"
                                        + summaryContent)
                ),
                "temperature", 0.5,
                "stream", true
        );

        return webClient.post()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                .filter(chunk -> !chunk.trim().equals("[DONE]"))
                .map(this::extractContentFromStream)
                .filter(content -> !content.isEmpty())
                // CHEKLANGAN qayta urinish: max 5 marta (cheksiz emas, prodakshenni resurs sarflashdan saqlaydi)
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(3))
                        .maxBackoff(Duration.ofMinutes(1))
                        .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Server band (429). Urinish {}/5", retrySignal.totalRetries() + 1))
                )
                .onErrorResume(WebClientResponseException.class, e ->
                        Flux.just("\n\n[API XATOSI]: " + e.getStatusCode() + " - " + e.getResponseBodyAsString()))
                .onErrorResume(Exception.class, e ->
                        Flux.just("\n\n[TIZIM XATOSI]: " + e.getMessage()));
    }

    /**
     * Matnni JSON ob'ektga aylantirib, ichidagi contentni olib beradi.
     */
    private String extractContentFromStream(String chunkString) {
        try {
            String jsonStr = chunkString.startsWith("data: ") ? chunkString.substring(6) : chunkString;
            if (jsonStr.trim().equals("[DONE]")) return "";

            JsonNode chunkNode = objectMapper.readTree(jsonStr);

            if (chunkNode.has("choices") && chunkNode.get("choices").isArray()
                    && chunkNode.get("choices").size() > 0) {
                JsonNode delta = chunkNode.get("choices").get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
