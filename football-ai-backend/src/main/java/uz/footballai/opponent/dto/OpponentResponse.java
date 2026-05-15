package uz.footballai.opponent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpponentResponse {

    private UUID id;
    private String name;
    private String league;
    private String city;
    private String typicalFormation;
    private String coachName;
    private String logoUrl;
    private String strengths;
    private String weaknesses;
    private String playStyle;
    private String keyPlayers;
    private String notes;
    private int matchCount;
}
