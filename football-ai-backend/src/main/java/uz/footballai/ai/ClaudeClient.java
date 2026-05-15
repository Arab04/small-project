package uz.footballai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import uz.footballai.ai.dto.ClaudeResponse;
import uz.footballai.config.ClaudeApiConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ClaudeClient {

    private final ClaudeApiConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeClient(
            ClaudeApiConfig config,
            @org.springframework.beans.factory.annotation.Qualifier("claudeHttpClient") OkHttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    /**
     * Claude API ga so'rov yuborish.
     * @param systemPrompt - system prompt (AI roli va vazifasi)
     * @param userMessage - foydalanuvchi xabari (ma'lumotlar va savol)
     * @return ClaudeResponse - javob va token statistikasi
     */
    public ClaudeResponse sendMessage(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getModel());
            requestBody.put("max_tokens", config.getMaxTokens());

            // System prompt
            ArrayNode systemArray = objectMapper.createArrayNode();
            ObjectNode systemBlock = objectMapper.createObjectNode();
            systemBlock.put("type", "text");
            systemBlock.put("text", systemPrompt);
            systemArray.add(systemBlock);
            requestBody.set("system", systemArray);

            // Messages
            ArrayNode messagesArray = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesArray.add(userMsg);
            requestBody.set("messages", messagesArray);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(config.getApiUrl())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-api-key", config.getApiKey())
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                    .build();

            log.info("Claude API ga so'rov yuborilmoqda... Model: {}", config.getModel());

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No body";
                    log.error("Claude API xatosi: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Claude API xatosi: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                JsonNode jsonResponse = objectMapper.readTree(responseBody);

                // Javob matnini olish
                String content = "";
                JsonNode contentArray = jsonResponse.get("content");
                if (contentArray != null && contentArray.isArray()) {
                    for (JsonNode block : contentArray) {
                        if ("text".equals(block.get("type").asText())) {
                            content = block.get("text").asText();
                            break;
                        }
                    }
                }

                // Token statistikasi
                int inputTokens = 0;
                int outputTokens = 0;
                JsonNode usage = jsonResponse.get("usage");
                if (usage != null) {
                    inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                    outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
                }

                log.info("Claude javob berdi. Input tokens: {}, Output tokens: {}", inputTokens, outputTokens);

                return ClaudeResponse.builder()
                        .content(content)
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .model(config.getModel())
                        .build();
            }
        } catch (IOException e) {
            log.error("Claude API bilan bog'lanishda xato: ", e);
            throw new RuntimeException("Claude API bilan bog'lanib bo'lmadi: " + e.getMessage(), e);
        }
    }
}
