package uz.footballai.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.ai.dto.AnalysisRequest;
import uz.footballai.ai.dto.AnalysisResponse;
import uz.footballai.common.ApiResponse;
import uz.footballai.common.PageResponse;

import java.util.UUID;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Tag(name = "AI Analysis", description = "AI yordamida taktik tahlil")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/request")
    @Operation(summary = "Yangi taktik tahlil so'rovi",
               description = "Raqibga qarshi taktik tahlil va g'alaba rejasini AI orqali yaratish. " +
                             "So'rov asinxron tarzda qayta ishlanadi.")
    public ResponseEntity<ApiResponse<AnalysisResponse>> requestAnalysis(
            @Valid @RequestBody AnalysisRequest request) {
        AnalysisResponse response = analysisService.requestAnalysis(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response, "Tahlil so'rovi qabul qilindi. Status: PENDING"));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Tahlil hisobotini ko'rish",
               description = "Status COMPLETED bo'lganda barcha ma'lumotlar to'liq qaytadi")
    public ResponseEntity<ApiResponse<AnalysisResponse>> getReport(@PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getReport(reportId)));
    }

    @GetMapping
    @Operation(summary = "Barcha tahlil hisobotlari")
    public ResponseEntity<ApiResponse<PageResponse<AnalysisResponse>>> getReports(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(analysisService.getReports(pageable)));
    }
}
