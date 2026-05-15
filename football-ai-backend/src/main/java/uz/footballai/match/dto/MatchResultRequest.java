package uz.footballai.match.dto;

import lombok.Data;

@Data
public class MatchResultRequest {
    private Integer ourScore;
    private Integer opponentScore;
    private String ourFormation;
    private String opponentFormation;
    private String notes;
}
