package uz.footballai.report;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.ai.AnalysisReport;
import uz.footballai.ai.AnalysisReportRepository;
import uz.footballai.ai.AnalysisStatus;
import uz.footballai.common.ApiResponse;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "PDF hisobotlar")
public class ReportController {

    private final PdfReportService pdfReportService;
    private final AnalysisReportRepository reportRepository;
    private final UserService userService;
    private final MinioClient minioClient;

    @Value("${minio.bucket.report}")
    private String reportBucket;

    @PostMapping("/{reportId}/generate-pdf")
    @Operation(summary = "Tahlil hisobotidan PDF yaratish")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePdf(@PathVariable UUID reportId) {
        User user = userService.getCurrentUser();

        AnalysisReport report = reportRepository
                .findByIdAndOurTeamClubIdAndDeletedFalse(reportId, user.getClub().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Report", "id", reportId));

        if (report.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException("PDF faqat tayyor (COMPLETED) tahlillar uchun yaratiladi. " +
                    "Hozirgi status: " + report.getStatus());
        }

        String objectName = pdfReportService.generateReport(reportId);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("pdfKey", objectName, "message", "PDF hisobot yaratildi"),
                "PDF yaratildi"
        ));
    }

    @GetMapping("/{reportId}/pdf-url")
    @Operation(summary = "PDF yuklab olish havolasi (2 soatga)")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPdfUrl(@PathVariable UUID reportId) {
        User user = userService.getCurrentUser();

        AnalysisReport report = reportRepository
                .findByIdAndOurTeamClubIdAndDeletedFalse(reportId, user.getClub().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Report", "id", reportId));

        if (report.getPdfUrl() == null) {
            throw new BusinessException("Bu tahlil uchun PDF hali yaratilmagan. " +
                    "Avval /reports/" + reportId + "/generate-pdf ga so'rov yuboring.");
        }

        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(reportBucket)
                            .object(report.getPdfUrl())
                            .method(Method.GET)
                            .expiry(2, TimeUnit.HOURS)
                            .build()
            );

            return ResponseEntity.ok(ApiResponse.ok(
                    Map.of("url", presignedUrl)
            ));
        } catch (Exception e) {
            throw new BusinessException("PDF havolasini olishda xato: " + e.getMessage());
        }
    }
}
