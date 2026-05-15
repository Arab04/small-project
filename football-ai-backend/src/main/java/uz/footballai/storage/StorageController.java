package uz.footballai.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

/**
 * MinIO'dan rasm, video va fayllarni serve qilish.
 * Heatmap, pass network PNG rasmlarini va annotated video'ni frontend'ga berish uchun.
 *
 * Endpoints:
 *   GET /api/storage/image?key=analytics/xxx/heatmap_team_1.png
 *   GET /api/storage/video?key=analytics/xxx/annotated_video.mp4
 */
@Slf4j
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {

    private final MinioClient minioClient;

    @Value("${minio.bucket.video}")
    private String videoBucket;

    @Value("${minio.bucket.image}")
    private String imageBucket;

    /**
     * MinIO'dan rasm olish — heatmap, pass network va boshqa analitika rasmlari.
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> getImage(@RequestParam String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] data = tryGetObject(videoBucket, key);
        if (data == null) {
            data = tryGetObject(imageBucket, key);
        }

        if (data == null) {
            log.warn("Rasm topilmadi: {} (ikkala bucket'da ham yo'q)", key);
            return ResponseEntity.notFound().build();
        }

        String contentType = "image/png";
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (key.endsWith(".svg")) {
            contentType = "image/svg+xml";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(data);
    }

    /**
     * MinIO'dan video olish — annotated tahlil videosi.
     * Streaming (Range request) qo'llab-quvvatlaydi — brauzerda play qilish uchun.
     *
     * @param key MinIO object key (masalan: analytics/uuid/annotated_video.mp4)
     */
    @GetMapping("/video")
    public ResponseEntity<?> getVideo(
            @RequestParam String key,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Avval fayl mavjudligi va hajmini tekshirish
        String bucket = videoBucket;
        StatObjectResponse stat = tryStatObject(bucket, key);
        if (stat == null) {
            bucket = imageBucket;
            stat = tryStatObject(bucket, key);
        }
        if (stat == null) {
            log.warn("Video topilmadi: {}", key);
            return ResponseEntity.notFound().build();
        }

        long fileSize = stat.size();
        String contentType = "video/mp4";
        if (key.endsWith(".avi")) {
            contentType = "video/x-msvideo";
        } else if (key.endsWith(".webm")) {
            contentType = "video/webm";
        }

        // Range request — brauzer video player uchun
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            try {
                String rangeSpec = rangeHeader.substring(6);
                String[] parts = rangeSpec.split("-");
                long rangeStart = Long.parseLong(parts[0]);
                long rangeEnd = parts.length > 1 && !parts[1].isEmpty()
                        ? Long.parseLong(parts[1])
                        : Math.min(rangeStart + 5 * 1024 * 1024 - 1, fileSize - 1); // 5MB chunk

                if (rangeEnd >= fileSize) {
                    rangeEnd = fileSize - 1;
                }
                long contentLength = rangeEnd - rangeStart + 1;

                InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucket)
                                .object(key)
                                .offset(rangeStart)
                                .length(contentLength)
                                .build()
                );

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .header(HttpHeaders.CONTENT_RANGE,
                                "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize)
                        .header("Accept-Ranges", "bytes")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(new InputStreamResource(is));

            } catch (Exception e) {
                log.error("Video range request xato: key={}, range={}", key, rangeHeader, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        // To'liq fayl — download yoki kichik videolar uchun
        try {
            InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header("Accept-Ranges", "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"annotated_video.mp4\"")
                    .body(new InputStreamResource(is));

        } catch (Exception e) {
            log.error("Video olishda xato: key={}", key, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Video'ni download qilish (attachment sifatida).
     */
    @GetMapping("/video/download")
    public ResponseEntity<?> downloadVideo(@RequestParam String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String bucket = videoBucket;
        StatObjectResponse stat = tryStatObject(bucket, key);
        if (stat == null) {
            bucket = imageBucket;
            stat = tryStatObject(bucket, key);
        }
        if (stat == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );

            String filename = key.contains("/")
                    ? key.substring(key.lastIndexOf('/') + 1)
                    : key;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(stat.size()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(new InputStreamResource(is));

        } catch (Exception e) {
            log.error("Video download xato: key={}", key, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private StatObjectResponse tryStatObject(String bucket, String key) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] tryGetObject(String bucket, String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );

            try (InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            )) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
