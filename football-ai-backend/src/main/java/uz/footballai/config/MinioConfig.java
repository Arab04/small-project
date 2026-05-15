package uz.footballai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket.video}")
    private String videoBucket;

    @Value("${minio.bucket.report}")
    private String reportBucket;

    @Value("${minio.bucket.image}")
    private String imageBucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        createBucketIfNotExists(client, videoBucket);
        createBucketIfNotExists(client, reportBucket);
        createBucketIfNotExists(client, imageBucket);

        return client;
    }

    private void createBucketIfNotExists(MinioClient client, String bucketName) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO bucket yaratildi: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO bucket yaratishda xato: {}", bucketName, e);
        }
    }
}
