package uz.footballai.player;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;
import uz.footballai.player.dto.PlayerRequest;
import uz.footballai.player.dto.PlayerResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teams/{teamId}/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "O'yinchilar boshqaruvi")
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping
    @Operation(summary = "Jamoaning barcha o'yinchilari")
    public ResponseEntity<ApiResponse<List<PlayerResponse>>> getPlayers(@PathVariable UUID teamId) {
        return ResponseEntity.ok(ApiResponse.ok(playerService.getPlayersByTeam(teamId)));
    }

    @PostMapping
    @Operation(summary = "Yangi o'yinchi qo'shish")
    public ResponseEntity<ApiResponse<PlayerResponse>> createPlayer(
            @PathVariable UUID teamId,
            @Valid @RequestBody PlayerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(playerService.createPlayer(teamId, request)));
    }

    @PutMapping("/{playerId}")
    @Operation(summary = "O'yinchi ma'lumotlarini yangilash")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayer(
            @PathVariable UUID teamId,
            @PathVariable UUID playerId,
            @Valid @RequestBody PlayerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(playerService.updatePlayer(playerId, request)));
    }

    @DeleteMapping("/{playerId}")
    @Operation(summary = "O'yinchini o'chirish")
    public ResponseEntity<ApiResponse<Void>> deletePlayer(
            @PathVariable UUID teamId,
            @PathVariable UUID playerId) {
        playerService.deletePlayer(playerId);
        return ResponseEntity.ok(ApiResponse.ok(null, "O'yinchi o'chirildi"));
    }
}
