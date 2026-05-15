package uz.footballai.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.footballai.player.Position;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRequest {

    @NotBlank(message = "O'yinchi ismi kiritilishi shart")
    private String fullName;

    private Integer jerseyNumber;

    @NotNull(message = "Pozitsiya kiritilishi shart")
    private Position position;

    private LocalDate birthDate;
    private Integer height;
    private Integer weight;
    private String preferredFoot;
    private String nationality;
    private String strengths;
    private String weaknesses;
    private String notes;
}
