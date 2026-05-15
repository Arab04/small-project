package uz.footballai.player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponse {

    private UUID id;
    private String fullName;
    private Integer jerseyNumber;
    private String position;
    private LocalDate birthDate;
    private Integer height;
    private Integer weight;
    private String preferredFoot;
    private String nationality;
    private String photoUrl;
    private String strengths;
    private String weaknesses;
    private String notes;
}
