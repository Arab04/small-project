package uz.footballai.config;

import lombok.Getter;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Getter
@Configuration
public class ClaudeApiConfig {

    @Value("${claude.api-key}")
    private String apiKey;

    @Value("${claude.api-url}")
    private String apiUrl;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.max-tokens}")
    private int maxTokens;

    @Value("${claude.timeout}")
    private int timeout;

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("claudeHttpClient")
    public OkHttpClient claudeHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
}
