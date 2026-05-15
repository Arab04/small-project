package uz.footballai.video;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;
import uz.footballai.match.Match;

import java.time.LocalDateTime;

/**
 * Video tahlil job entity.
 *
 * 90 daqiqalik video uchun yangi maydonlar:
 *  - currentMinute, totalMinutes (qaysi daqiqa tahlil qilinmoqda)
 *  - currentPhase (FIRST_HALF/HALFTIME/SECOND_HALF)
 *  - etaSeconds (taxminiy qolgan vaqt)
 *  - isLongForm (90 min mode'da ekanligi)
 *  - lastCheckpointFrame (resume uchun)
 *  - phasesDetected, timelineWindowsCount, staminaDetected (yakuniy meta)
 */
@Entity
@Table(name = "video_analysis_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    @JsonIgnore
    private Match match;

    @Column(nullable = false)
    private String videoMinioKey;

    private String mlJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VideoJobStatus status = VideoJobStatus.PENDING;

    @Builder.Default
    private Integer progress = 0;

    private String currentStep;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Builder.Default
    private Integer eventsDetected = 0;

    @Builder.Default
    private Integer framesProcessed = 0;

    private Double averageConfidence;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore  // har doim qaytarmaymiz - katta JSON
    private String rawResultJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // ========== YANGI: 90-min progress ==========

    /** Hozir tahlil qilinayotgan daqiqa (videoning) */
    private Double currentMinute;

    /** Jami videoning daqiqasi */
    private Double totalMinutes;

    /** Tahlilning qaysi yarmi (FIRST_HALF/HALFTIME/SECOND_HALF) */
    private String currentPhase;

    /** Taxminiy qolgan vaqt (sek) */
    private Integer etaSeconds;

    /** 90 daqiqalik mode'da ekanligi */
    @Builder.Default
    private Boolean isLongForm = false;

    /** Crash bo'lsa - shu freymdan resume qilinadi */
    private Integer lastCheckpointFrame;

    // ========== Yakuniy meta (COMPLETED da to'ldiriladi) ==========

    /** Necha ta phase aniqlandi (1-yarim/tanaffus/2-yarim = 3) */
    private Integer phasesDetected;

    /** Necha ta 5-daqiqa snapshot */
    private Integer timelineWindowsCount;

    /** Charchash signali aniqlandimi (har 2 kamanda uchun) */
    @Builder.Default
    private Boolean homeStaminaDetected = false;
    @Builder.Default
    private Boolean awayStaminaDetected = false;
}
