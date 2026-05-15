package uz.footballai.opponent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpponentRequest {

    @NotBlank(message = "Raqib nomi kiritilishi shart")
    private String name;

    private String league;
    private String city;
    private String typicalFormation;
    private String coachName;
    private String strengths;
    private String weaknesses;
    private String playStyle;
    private String keyPlayers;
    private String notes;
}
