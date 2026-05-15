package uz.footballai.match;

import jakarta.persistence.*;
import lombok.*;
import uz.footballai.common.BaseEntity;

@Entity
@Table(name = "match_stats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStats extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private Match match;

    // ===== Bizning jamoamiz statistikasi =====
    private Integer ourPossession;       // %
    private Integer ourTotalShots;
    private Integer ourShotsOnTarget;
    private Integer ourCorners;
    private Integer ourFouls;
    private Integer ourOffsides;
    private Double ourPassAccuracy;      // %
    private Integer ourYellowCards;
    private Integer ourRedCards;
    private Integer ourSaves;
    private Double ourXg;                // expected goals

    // ===== Raqib statistikasi =====
    private Integer oppPossession;
    private Integer oppTotalShots;
    private Integer oppShotsOnTarget;
    private Integer oppCorners;
    private Integer oppFouls;
    private Integer oppOffsides;
    private Double oppPassAccuracy;
    private Integer oppYellowCards;
    private Integer oppRedCards;
    private Integer oppSaves;
    private Double oppXg;

    // ===== Qo'shimcha statistika =====
    private Integer ourDuelsWon;
    private Integer oppDuelsWon;
    private Integer ourAerialDuelsWon;
    private Integer oppAerialDuelsWon;
    private Integer ourTackles;
    private Integer oppTackles;
    private Integer ourInterceptions;
    private Integer oppInterceptions;
    private Double ourDistanceCovered;   // km
    private Double oppDistanceCovered;
}
