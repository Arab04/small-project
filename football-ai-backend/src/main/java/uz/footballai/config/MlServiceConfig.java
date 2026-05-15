package uz.footballai.config;

import lombok.Getter;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Getter
@Configuration
public class MlServiceConfig {

    @Value("${ml-service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${ml-service.timeout:300}")
    private int timeoutSeconds;

    @Value("${ml-service.internal-token:internal-secret-token}")
    private String internalToken;

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("mlServiceHttpClient")
    public OkHttpClient mlServiceHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
}
