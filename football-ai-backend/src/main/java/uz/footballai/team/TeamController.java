package uz.footballai.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;
import uz.footballai.team.dto.TeamRequest;
import uz.footballai.team.dto.TeamResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Jamoa boshqaruvi")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "Klubning barcha jamoalari")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> getMyTeams() {
        return ResponseEntity.ok(ApiResponse.ok(teamService.getMyTeams()));
    }

    @PostMapping
    @Operation(summary = "Yangi jamoa yaratish")
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(@Valid @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(teamService.createTeam(request)));
    }

    @PutMapping("/{teamId}")
    @Operation(summary = "Jamoani yangilash")
    public ResponseEntity<ApiResponse<TeamResponse>> updateTeam(
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.updateTeam(teamId, request)));
    }

    @DeleteMapping("/{teamId}")
    @Operation(summary = "Jamoani o'chirish")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable UUID teamId) {
        teamService.deleteTeam(teamId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Jamoa o'chirildi"));
    }
}
