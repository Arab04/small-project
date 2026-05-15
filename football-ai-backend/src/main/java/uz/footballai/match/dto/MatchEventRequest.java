package uz.footballai.match.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.footballai.match.MatchEventType;
import uz.footballai.match.TeamSide;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchEventRequest {

    @NotNull(message = "Daqiqa kiritilishi shart")
    private Integer minute;

    private Integer additionalMinute;

    @NotNull(message = "Voqea turi kiritilishi shart")
    private MatchEventType type;

    @NotNull(message = "Jamoa tomoni kiritilishi shart")
    private TeamSide teamSide;

    private String playerName;
    private String secondPlayerName;
    private String description;
    private Double xCoordinate;
    private Double yCoordinate;
}
