package uz.footballai.match.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Match yaratish so'rovi — ikkala formatni qo'llab-quvvatlaydi:
 *
 * 1. UUID asosida (eski format):
 *    { ourTeamId, opponentId, matchDate, isHome, venue, competition, ourFormation, opponentFormation, notes }
 *
 * 2. Nomlar asosida (yangi frontend format — auto-create teams/opponents):
 *    { homeTeamName, awayTeamName, kickoffAt, homeScore, awayScore, league, venue, matchday }
 *
 * MatchService.createMatch() dispatcher ikkala formatni qo\'llab quvvatlaydi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchCreateRequest {

    // ===== UUID asosida (eski format) =====
    private UUID ourTeamId;
    private UUID opponentId;
    private LocalDate matchDate;
    private boolean isHome;
    private String competition;
    private String ourFormation;
    private String opponentFormation;
    private String notes;

    // ===== Nomlar asosida (yangi format) =====
    private String homeTeamName;
    private String awayTeamName;
    private LocalDateTime kickoffAt;
    private Integer homeScore;
    private Integer awayScore;
    private String league;
    private Integer matchday;

    // ===== Ikkala formatda umumiy =====
    private String venue;
}
