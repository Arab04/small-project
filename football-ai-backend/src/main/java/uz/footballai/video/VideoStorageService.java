package uz.footballai.video;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.footballai.common.exception.BusinessException;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Video storage service.
 *
 * 90 daqiqalik video uchun yangiliklar:
 *  - MAX_VIDEO_SIZE 500MB → 3GB ga oshirildi
 *  - Long-form video uchun chunked upload tavsiya etiladi
 *  - Pre-signed URL muddati 6 soatga uzaytirildi (long video uploadlar uchun)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.video}")
    private String videoBucket;

    @Value("${minio.bucket.image}")
    private String imageBucket;

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/avi", "video/mpeg", "video/quicktime",
            "video/x-msvideo", "video/x-matroska", "video/webm"
    );

    /** 3 GB - 90 daqiqalik 1080p video uchun */
    private static final long MAX_VIDEO_SIZE = 3L * 1024 * 1024 * 1024;

    /** 100 MB - kichik video chegarasi (basic plan uchun) */
    private static final long BASIC_PLAN_MAX_SIZE = 500L * 1024 * 1024;

    /**
     * Video faylni MinIO ga yuklash.
     */
    public String uploadVideo(MultipartFile file, UUID matchId) {
        validateVideoFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".mp4";

        String objectName = String.format("matches/%s/%s%s",
                matchId, UUID.randomUUID(), extension);

        try (InputStream inputStream = file.getInputStream()) {
            long sizeMb = file.getSize() / (1024 * 1024);
            log.info("Video yuklash boshlandi: {} ({}MB)", objectName, sizeMb);

            // Long-form video (>500MB) uchun chunked upload
            long partSize = file.getSize() > BASIC_PLAN_MAX_SIZE
                    ? 50L * 1024 * 1024   // 50MB chunks
                    : 10L * 1024 * 1024;  // 10MB chunks

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(videoBucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), partSize)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Video yuklandi: {} ({}MB)", objectName, sizeMb);
            return objectName;

        } catch (Exception e) {
            log.error("Video yuklashda xato: ", e);
            throw new BusinessException("Video yuklashda xato: " + e.getMessage());
        }
    }

    /**
     * Pre-signed URL olish (long-form video uchun 6 soat muddat).
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(videoBucket)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(6, TimeUnit.HOURS)  // long-form upload uchun
                            .build()
            );
        } catch (Exception e) {
            log.error("Pre-signed URL olishda xato: ", e);
            throw new BusinessException("Video havolasini olishda xato");
        }
    }

    public String uploadImage(MultipartFile file, String folder) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String objectName = String.format("%s/%s%s", folder, UUID.randomUUID(), extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(imageBucket)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return objectName;
        } catch (Exception e) {
            log.error("Rasm yuklashda xato: ", e);
            throw new BusinessException("Rasm yuklashda xato");
        }
    }

    public void deleteFile(String bucket, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Fayl o'chirishda xato: {} - {}", objectName, e.getMessage());
        }
    }

    private void validateVideoFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Fayl bo'sh");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new BusinessException(String.format(
                    "Video hajmi %d GB dan oshmasligi kerak. Sizniki: %.2f GB",
                    MAX_VIDEO_SIZE / (1024 * 1024 * 1024),
                    file.getSize() / (1024.0 * 1024 * 1024)));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new BusinessException("Faqat video formatlar ruxsat etiladi: MP4, AVI, MKV, WEBM");
        }
    }
}
