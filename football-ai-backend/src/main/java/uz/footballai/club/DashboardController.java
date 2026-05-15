package uz.footballai.club;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.footballai.ai.AnalysisReportRepository;
import uz.footballai.common.ApiResponse;
import uz.footballai.match.MatchRepository;
import uz.footballai.opponent.OpponentRepository;
import uz.footballai.team.TeamRepository;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Klub umumiy statistikasi")
public class DashboardController {

    private final UserService userService;
    private final TeamRepository teamRepository;
    private final OpponentRepository opponentRepository;
    private final MatchRepository matchRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ClubRepository clubRepository;

    @GetMapping
    @Operation(summary = "Dashboard statistikasi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        User user = userService.getCurrentUser();
        UUID clubId = user.getClub().getId();
        Club club = user.getClub();

        Map<String, Object> dashboard = Map.of(
                "clubName", club.getName(),
                "subscriptionPlan", club.getSubscriptionPlan().name(),
                "teamsCount", teamRepository.countByClubIdAndDeletedFalse(clubId),
                "opponentsCount", opponentRepository.countByClubIdAndDeletedFalse(clubId),
                "matchesCount", matchRepository.countByClubIdAndDeletedFalse(clubId),
                "analysesUsed", club.getAnalysesUsedThisMonth(),
                "analysesLimit", club.getMaxAnalysesPerMonth(),
                "analysesRemaining", Math.max(0, club.getMaxAnalysesPerMonth() - club.getAnalysesUsedThisMonth())
        );

        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }
}
