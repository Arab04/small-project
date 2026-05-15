package uz.footballai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    private UUID id;
    private String status;
    private String ourTeamName;
    private String opponentName;

    private String opponentStrengths;
    private String opponentWeaknesses;
    private String ourStrengths;
    private String ourWeaknesses;
    private String recommendedFormation;
    private String tacticalPlan;
    private String attackingPlan;
    private String defendingPlan;
    private String setPiecePlan;
    private String firstFifteenMinutesPlan;
    private String keyPlayersToWatch;
    private String substitutionStrategy;
    private String pressZones;
    private String dangerZones;
    private String summary;

    private String aiModel;
    private Integer totalTokens;
    private BigDecimal generationCost;
    private String pdfUrl;

    private LocalDateTime createdAt;
    private String errorMessage;
}
