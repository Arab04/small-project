package uz.footballai.report;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.footballai.ai.AnalysisReport;
import uz.footballai.ai.AnalysisReportRepository;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final AnalysisReportRepository reportRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket.report}")
    private String reportBucket;

    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(17, 24, 39);      // Dark
    private static final DeviceRgb ACCENT_COLOR = new DeviceRgb(37, 99, 235);       // Blue
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(22, 163, 74);      // Green
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(220, 38, 38);       // Red
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(243, 244, 246);         // Light gray

    /**
     * PDF hisobot yaratish va MinIO ga saqlash.
     * @return MinIO object key
     */
    public String generateReport(UUID reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", "id", reportId));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // ===== TITLE PAGE =====
            document.add(new Paragraph("\n\n\n"));

            document.add(new Paragraph("TAKTIK TAHLIL HISOBOTI")
                    .setFont(boldFont).setFontSize(28)
                    .setFontColor(PRIMARY_COLOR)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            document.add(new Paragraph(
                    report.getOurTeam().getName() + "  vs  " + report.getOpponent().getName())
                    .setFont(boldFont).setFontSize(22)
                    .setFontColor(ACCENT_COLOR)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            if (report.getRecommendedFormation() != null) {
                document.add(new Paragraph("Tavsiya etilgan formatsiya: " + report.getRecommendedFormation())
                        .setFont(boldFont).setFontSize(16)
                        .setFontColor(SUCCESS_COLOR)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            document.add(new Paragraph("\n\n"));

            document.add(new Paragraph("Yaratilgan: " +
                    report.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                    .setFont(regularFont).setFontSize(11)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("AI model: " + (report.getAiModel() != null ? report.getAiModel() : "N/A"))
                    .setFont(regularFont).setFontSize(10)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            // ===== XULOSA =====
            if (report.getSummary() != null) {
                pdfDoc.addNewPage();
                addSectionTitle(document, boldFont, "QISQA XULOSA");
                document.add(new Paragraph(report.getSummary())
                        .setFont(regularFont).setFontSize(12)
                        .setBackgroundColor(LIGHT_BG)
                        .setPadding(15));
            }

            // ===== KUCHLI / ZAIF TOMONLAR JADVALI =====
            pdfDoc.addNewPage();
            addSectionTitle(document, boldFont, "KUCHLI VA ZAIF TOMONLAR TAQQOSLASH");

            Table comparisonTable = new Table(UnitValue.createPercentArray(new float[]{25, 37.5f, 37.5f}))
                    .useAllAvailableWidth();

            // Header
            comparisonTable.addHeaderCell(createHeaderCell("", boldFont));
            comparisonTable.addHeaderCell(createHeaderCell(report.getOurTeam().getName(), boldFont));
            comparisonTable.addHeaderCell(createHeaderCell(report.getOpponent().getName(), boldFont));

            // Kuchli tomonlar
            comparisonTable.addCell(createLabelCell("Kuchli tomonlar", boldFont, SUCCESS_COLOR));
            comparisonTable.addCell(createContentCell(report.getOurStrengths(), regularFont));
            comparisonTable.addCell(createContentCell(report.getOpponentStrengths(), regularFont));

            // Zaif tomonlar
            comparisonTable.addCell(createLabelCell("Zaif tomonlar", boldFont, DANGER_COLOR));
            comparisonTable.addCell(createContentCell(report.getOurWeaknesses(), regularFont));
            comparisonTable.addCell(createContentCell(report.getOpponentWeaknesses(), regularFont));

            document.add(comparisonTable);

            // ===== TAKTIK REJA =====
            if (report.getTacticalPlan() != null) {
                pdfDoc.addNewPage();
                addSectionTitle(document, boldFont, "UMUMIY TAKTIK REJA");
                document.add(new Paragraph(report.getTacticalPlan())
                        .setFont(regularFont).setFontSize(11));
            }

            // ===== HUJUM REJASI =====
            if (report.getAttackingPlan() != null) {
                addSectionTitle(document, boldFont, "HUJUM REJASI");
                document.add(new Paragraph(report.getAttackingPlan())
                        .setFont(regularFont).setFontSize(11)
                        .setBackgroundColor(new DeviceRgb(239, 246, 255))
                        .setPadding(10));
            }

            // ===== HIMOYA REJASI =====
            if (report.getDefendingPlan() != null) {
                addSectionTitle(document, boldFont, "HIMOYA REJASI");
                document.add(new Paragraph(report.getDefendingPlan())
                        .setFont(regularFont).setFontSize(11)
                        .setBackgroundColor(new DeviceRgb(254, 242, 242))
                        .setPadding(10));
            }

            // ===== STANDART HOLATLAR =====
            if (report.getSetPiecePlan() != null) {
                addSectionTitle(document, boldFont, "STANDART HOLATLAR (burchak, jarima, autt)");
                document.add(new Paragraph(report.getSetPiecePlan())
                        .setFont(regularFont).setFontSize(11));
            }

            // ===== BIRINCHI 15 DAQIQA =====
            if (report.getFirstFifteenMinutesPlan() != null) {
                pdfDoc.addNewPage();
                addSectionTitle(document, boldFont, "BIRINCHI 15 DAQIQA REJASI");
                document.add(new Paragraph(report.getFirstFifteenMinutesPlan())
                        .setFont(regularFont).setFontSize(11)
                        .setBackgroundColor(new DeviceRgb(255, 251, 235))
                        .setPadding(10));
            }

            // ===== XAVFLI O'YINCHILAR =====
            if (report.getKeyPlayersToWatch() != null) {
                addSectionTitle(document, boldFont, "RAQIBNING XAVFLI O'YINCHILARI");
                document.add(new Paragraph(report.getKeyPlayersToWatch())
                        .setFont(regularFont).setFontSize(11));
            }

            // ===== PRESSING VA XAVFLI ZONALAR =====
            if (report.getPressZones() != null) {
                addSectionTitle(document, boldFont, "PRESSING ZONALARI");
                document.add(new Paragraph(report.getPressZones())
                        .setFont(regularFont).setFontSize(11));
            }

            if (report.getDangerZones() != null) {
                addSectionTitle(document, boldFont, "XAVFLI ZONALAR");
                document.add(new Paragraph(report.getDangerZones())
                        .setFont(regularFont).setFontSize(11));
            }

            // ===== ALMASHTIRISHLAR =====
            if (report.getSubstitutionStrategy() != null) {
                addSectionTitle(document, boldFont, "ALMASHTIRISHLAR STRATEGIYASI");
                document.add(new Paragraph(report.getSubstitutionStrategy())
                        .setFont(regularFont).setFontSize(11));
            }

            // ===== FOOTER =====
            document.add(new Paragraph("\n\n"));
            document.add(new Paragraph("Football AI Analysis Platform — footballai.uz")
                    .setFont(regularFont).setFontSize(9)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            document.close();

            // MinIO ga yuklash
            byte[] pdfBytes = baos.toByteArray();
            String objectName = String.format("reports/%s/%s_vs_%s_%s.pdf",
                    report.getOurTeam().getClub().getId(),
                    report.getOurTeam().getName().replaceAll("\\s+", "_"),
                    report.getOpponent().getName().replaceAll("\\s+", "_"),
                    report.getId().toString().substring(0, 8));

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(reportBucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(pdfBytes), pdfBytes.length, -1)
                            .contentType("application/pdf")
                            .build()
            );

            // PDF URL ni report ga saqlash
            report.setPdfUrl(objectName);
            reportRepository.save(report);

            log.info("PDF hisobot yaratildi: {} ({} KB)", objectName, pdfBytes.length / 1024);
            return objectName;

        } catch (Exception e) {
            log.error("PDF yaratishda xato: ", e);
            throw new BusinessException("PDF hisobot yaratishda xato: " + e.getMessage());
        }
    }

    private void addSectionTitle(Document document, PdfFont boldFont, String title) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph(title)
                .setFont(boldFont).setFontSize(14)
                .setFontColor(ACCENT_COLOR)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(ACCENT_COLOR, 1))
                .setPaddingBottom(5)
                .setMarginBottom(10));
    }

    private Cell createHeaderCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(11))
                .setBackgroundColor(PRIMARY_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private Cell createLabelCell(String text, PdfFont font, DeviceRgb color) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(10).setFontColor(color))
                .setPadding(8)
                .setBackgroundColor(LIGHT_BG);
    }

    private Cell createContentCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text != null ? text : "Ma'lumot kiritilmagan")
                        .setFont(font).setFontSize(10))
                .setPadding(8);
    }
}
