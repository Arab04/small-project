package uz.footballai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.ai.dto.AnalysisRequest;
import uz.footballai.ai.dto.AnalysisResponse;
import uz.footballai.ai.dto.ClaudeResponse;
import uz.footballai.club.Club;
import uz.footballai.common.PageResponse;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.match.Match;
import uz.footballai.match.MatchService;
import uz.footballai.opponent.Opponent;
import uz.footballai.opponent.OpponentService;
import uz.footballai.team.Team;
import uz.footballai.team.TeamService;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisReportRepository reportRepository;
    private final ClaudeClient claudeClient;
    private final PromptBuilder promptBuilder;
    private final UserService userService;
    private final TeamService teamService;
    private final OpponentService opponentService;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;

    // Taxminiy narxlar (Claude Sonnet 4)
    private static final BigDecimal INPUT_TOKEN_PRICE = new BigDecimal("0.000003");   // $3 per 1M
    private static final BigDecimal OUTPUT_TOKEN_PRICE = new BigDecimal("0.000015");  // $15 per 1M

    /**
     * Tahlil so'rovini yaratish va asinxron ishga tushirish.
     */
    @Transactional
    public AnalysisResponse requestAnalysis(AnalysisRequest request) {
        User user = userService.getCurrentUser();
        Club club = user.getClub();
        UUID clubId = club.getId();

        // Limit tekshiruvi
        if (!club.canRequestAnalysis()) {
            throw new BusinessException(
                    String.format("Oylik tahlil limiti tugadi. Ishlatilgan: %d/%d. Tarifni yangilang.",
                            club.getAnalysesUsedThisMonth(), club.getMaxAnalysesPerMonth()));
        }

        Team ourTeam = teamService.findByIdAndClub(request.getOurTeamId(), clubId);
        Opponent opponent = opponentService.findByIdAndClub(request.getOpponentId(), clubId);

        // Hisobot yaratish
        AnalysisReport report = AnalysisReport.builder()
                .requestedBy(user)
                .ourTeam(ourTeam)
                .opponent(opponent)
                .status(AnalysisStatus.PENDING)
                .build();

        report = reportRepository.save(report);

        // Limitni oshirish
        club.incrementAnalysisCount();

        // Asinxron tahlilni boshlash
        processAnalysisAsync(report.getId());

        log.info("Tahlil so'rovi yaratildi: {} vs {} (ID: {})",
                ourTeam.getName(), opponent.getName(), report.getId());

        return toResponse(report);
    }

    /**
     * Asinxron tahlil jarayoni — alohida threadda ishlaydi.
     */
    @Async
    public void processAnalysisAsync(UUID reportId) {
        try {
            AnalysisReport report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new ResourceNotFoundException("Report", "id", reportId));

            report.setStatus(AnalysisStatus.IN_PROGRESS);
            reportRepository.save(report);

            // Ma'lumotlarni olish
            Team ourTeam = report.getOurTeam();
            Opponent opponent = report.getOpponent();
            List<Match> opponentMatches = matchService.getFinishedMatchesByOpponent(opponent.getId());
            List<Match> ourMatches = matchService.getFinishedMatchesByTeam(ourTeam.getId());

            // Tahlil qilingan o'yinlar ID'larini saqlash
            String matchIds = opponentMatches.stream()
                    .map(m -> m.getId().toString())
                    .collect(Collectors.joining(","));
            report.setAnalyzedMatchIds(matchIds);

            // Prompt yasash
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userMessage = promptBuilder.buildUserMessage(ourTeam, opponent, opponentMatches, ourMatches);

            log.info("Claude ga so'rov yuborilmoqda. O'yinlar soni: raqib={}, bizning={}",
                    opponentMatches.size(), ourMatches.size());

            // Claude API ga so'rov
            ClaudeResponse claudeResponse = claudeClient.sendMessage(systemPrompt, userMessage);

            // JSON javobni parse qilish
            parseAndSaveResponse(report, claudeResponse);

            report.setStatus(AnalysisStatus.COMPLETED);
            reportRepository.save(report);

            log.info("Tahlil muvaffaqiyatli yakunlandi: {}", reportId);

        } catch (Exception e) {
            log.error("Tahlil xatosi (ID: {}): ", reportId, e);
            try {
                AnalysisReport report = reportRepository.findById(reportId).orElse(null);
                if (report != null) {
                    report.setStatus(AnalysisStatus.FAILED);
                    report.setErrorMessage(e.getMessage());
                    reportRepository.save(report);
                }
            } catch (Exception ex) {
                log.error("Xato holatini saqlab bo'lmadi: ", ex);
            }
        }
    }

    private void parseAndSaveResponse(AnalysisReport report, ClaudeResponse claudeResponse) {
        try {
            String content = claudeResponse.getContent().trim();

            // JSON bloklarni tozalash
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();

            JsonNode json = objectMapper.readTree(content);

            report.setOpponentStrengths(getTextOrNull(json, "opponent_strengths"));
            report.setOpponentWeaknesses(getTextOrNull(json, "opponent_weaknesses"));
            report.setOurStrengths(getTextOrNull(json, "our_strengths"));
            report.setOurWeaknesses(getTextOrNull(json, "our_weaknesses"));
            report.setRecommendedFormation(getTextOrNull(json, "recommended_formation"));
            report.setTacticalPlan(getTextOrNull(json, "tactical_plan"));
            report.setAttackingPlan(getTextOrNull(json, "attacking_plan"));
            report.setDefendingPlan(getTextOrNull(json, "defending_plan"));
            report.setSetPiecePlan(getTextOrNull(json, "set_piece_plan"));
            report.setFirstFifteenMinutesPlan(getTextOrNull(json, "first_fifteen_minutes_plan"));
            report.setKeyPlayersToWatch(getTextOrNull(json, "key_players_to_watch"));
            report.setSubstitutionStrategy(getTextOrNull(json, "substitution_strategy"));
            report.setPressZones(getTextOrNull(json, "press_zones"));
            report.setDangerZones(getTextOrNull(json, "danger_zones"));
            report.setSummary(getTextOrNull(json, "summary"));

            // AI metadata
            report.setAiModel(claudeResponse.getModel());
            report.setPromptTokens(claudeResponse.getInputTokens());
            report.setCompletionTokens(claudeResponse.getOutputTokens());

            // Xarajat hisoblash
            BigDecimal cost = INPUT_TOKEN_PRICE.multiply(BigDecimal.valueOf(claudeResponse.getInputTokens()))
                    .add(OUTPUT_TOKEN_PRICE.multiply(BigDecimal.valueOf(claudeResponse.getOutputTokens())))
                    .setScale(6, RoundingMode.HALF_UP);
            report.setGenerationCost(cost);

        } catch (Exception e) {
            log.error("Claude javobini parse qilishda xato: ", e);
            // Raw content ni saqlash
            report.setTacticalPlan(claudeResponse.getContent());
            report.setAiModel(claudeResponse.getModel());
            report.setPromptTokens(claudeResponse.getInputTokens());
            report.setCompletionTokens(claudeResponse.getOutputTokens());
        }
    }

    private String getTextOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    // ===== READ OPERATIONS =====

    @Transactional(readOnly = true)
    public AnalysisResponse getReport(UUID reportId) {
        User user = userService.getCurrentUser();
        AnalysisReport report = reportRepository
                .findByIdAndOurTeamClubIdAndDeletedFalse(reportId, user.getClub().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Analysis Report", "id", reportId));
        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalysisResponse> getReports(Pageable pageable) {
        User user = userService.getCurrentUser();
        var page = reportRepository
                .findByOurTeamClubIdAndDeletedFalse(user.getClub().getId(), pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    private AnalysisResponse toResponse(AnalysisReport report) {
        return AnalysisResponse.builder()
                .id(report.getId())
                .status(report.getStatus().name())
                .ourTeamName(report.getOurTeam().getName())
                .opponentName(report.getOpponent().getName())
                .opponentStrengths(report.getOpponentStrengths())
                .opponentWeaknesses(report.getOpponentWeaknesses())
                .ourStrengths(report.getOurStrengths())
                .ourWeaknesses(report.getOurWeaknesses())
                .recommendedFormation(report.getRecommendedFormation())
                .tacticalPlan(report.getTacticalPlan())
                .attackingPlan(report.getAttackingPlan())
                .defendingPlan(report.getDefendingPlan())
                .setPiecePlan(report.getSetPiecePlan())
                .firstFifteenMinutesPlan(report.getFirstFifteenMinutesPlan())
                .keyPlayersToWatch(report.getKeyPlayersToWatch())
                .substitutionStrategy(report.getSubstitutionStrategy())
                .pressZones(report.getPressZones())
                .dangerZones(report.getDangerZones())
                .summary(report.getSummary())
                .aiModel(report.getAiModel())
                .totalTokens(report.getPromptTokens() != null && report.getCompletionTokens() != null
                        ? report.getPromptTokens() + report.getCompletionTokens() : null)
                .generationCost(report.getGenerationCost())
                .pdfUrl(report.getPdfUrl())
                .createdAt(report.getCreatedAt())
                .errorMessage(report.getErrorMessage())
                .build();
    }
}
