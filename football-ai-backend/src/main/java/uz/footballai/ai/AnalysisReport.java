package uz.footballai.ai;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;
import uz.footballai.opponent.Opponent;
import uz.footballai.team.Team;
import uz.footballai.user.User;

import java.math.BigDecimal;

@Entity
@Table(name = "analysis_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "our_team_id", nullable = false)
    private Team ourTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id", nullable = false)
    private Opponent opponent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AnalysisStatus status = AnalysisStatus.PENDING;

    // Tahlil natijalari
    @Column(columnDefinition = "TEXT")
    private String analyzedMatchIds;  // JSON: qaysi o'yinlar tahlil qilindi

    @Column(columnDefinition = "TEXT")
    private String opponentStrengths;

    @Column(columnDefinition = "TEXT")
    private String opponentWeaknesses;

    @Column(columnDefinition = "TEXT")
    private String ourStrengths;

    @Column(columnDefinition = "TEXT")
    private String ourWeaknesses;

    private String recommendedFormation;

    @Column(columnDefinition = "TEXT")
    private String tacticalPlan;

    @Column(columnDefinition = "TEXT")
    private String attackingPlan;

    @Column(columnDefinition = "TEXT")
    private String defendingPlan;

    @Column(columnDefinition = "TEXT")
    private String setPiecePlan;

    @Column(columnDefinition = "TEXT")
    private String firstFifteenMinutesPlan;

    @Column(columnDefinition = "TEXT")
    private String keyPlayersToWatch;

    @Column(columnDefinition = "TEXT")
    private String substitutionStrategy;

    @Column(columnDefinition = "TEXT")
    private String pressZones;

    @Column(columnDefinition = "TEXT")
    private String dangerZones;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // AI metadata
    private String aiModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal generationCost;

    // PDF
    private String pdfUrl;

    // Xato holati
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
