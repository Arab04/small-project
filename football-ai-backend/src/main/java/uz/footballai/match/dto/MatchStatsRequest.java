package uz.footballai.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStatsRequest {

    private Integer ourPossession;
    private Integer ourTotalShots;
    private Integer ourShotsOnTarget;
    private Integer ourCorners;
    private Integer ourFouls;
    private Integer ourOffsides;
    private Double ourPassAccuracy;
    private Integer ourYellowCards;
    private Integer ourRedCards;
    private Integer ourSaves;
    private Double ourXg;

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

    private Integer ourDuelsWon;
    private Integer oppDuelsWon;
    private Integer ourAerialDuelsWon;
    private Integer oppAerialDuelsWon;
    private Integer ourTackles;
    private Integer oppTackles;
    private Integer ourInterceptions;
    private Integer oppInterceptions;
    private Double ourDistanceCovered;
    private Double oppDistanceCovered;
}
