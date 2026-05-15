package uz.footballai.opponent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;
import uz.footballai.common.PageResponse;
import uz.footballai.opponent.dto.OpponentRequest;
import uz.footballai.opponent.dto.OpponentResponse;

import java.util.UUID;

@RestController
@RequestMapping("/opponents")
@RequiredArgsConstructor
@Tag(name = "Opponents", description = "Raqib jamoalar boshqaruvi")
public class OpponentController {

    private final OpponentService opponentService;

    @GetMapping
    @Operation(summary = "Barcha raqiblar ro'yxati")
    public ResponseEntity<ApiResponse<PageResponse<OpponentResponse>>> getOpponents(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opponentService.getOpponents(pageable)));
    }

    @GetMapping("/{opponentId}")
    @Operation(summary = "Raqib haqida batafsil ma'lumot")
    public ResponseEntity<ApiResponse<OpponentResponse>> getOpponent(@PathVariable UUID opponentId) {
        return ResponseEntity.ok(ApiResponse.ok(opponentService.getOpponent(opponentId)));
    }

    @PostMapping
    @Operation(summary = "Yangi raqib qo'shish")
    public ResponseEntity<ApiResponse<OpponentResponse>> createOpponent(
            @Valid @RequestBody OpponentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(opponentService.createOpponent(request)));
    }

    @PutMapping("/{opponentId}")
    @Operation(summary = "Raqib ma'lumotlarini yangilash")
    public ResponseEntity<ApiResponse<OpponentResponse>> updateOpponent(
            @PathVariable UUID opponentId,
            @Valid @RequestBody OpponentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(opponentService.updateOpponent(opponentId, request)));
    }

    @DeleteMapping("/{opponentId}")
    @Operation(summary = "Raqibni o'chirish")
    public ResponseEntity<ApiResponse<Void>> deleteOpponent(@PathVariable UUID opponentId) {
        opponentService.deleteOpponent(opponentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Raqib o'chirildi"));
    }
}
