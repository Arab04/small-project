package uz.footballai.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    private UUID id;
    private UUID ourTeamId;
    private String ourTeamName;
    private UUID opponentId;
    private String opponentName;
    private boolean isHome;
    private LocalDate matchDate;
    private String venue;
    private String competition;
    private Integer ourScore;
    private Integer opponentScore;
    private String result;           // WIN, LOSS, DRAW
    private String scoreDisplay;     // "2:1"
    private String ourFormation;
    private String opponentFormation;
    private String status;
    private String videoUrl;
    private String notes;
    private int eventCount;
    private boolean hasStats;
}
