package uz.footballai.match;

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
import uz.footballai.match.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "O'yinlar boshqaruvi")
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    @Operation(summary = "Barcha o'yinlar ro'yxati")
    public ResponseEntity<ApiResponse<PageResponse<MatchResponse>>> getMatches(
            @PageableDefault(size = 20, sort = "matchDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(matchService.getMatches(pageable)));
    }

    @GetMapping("/{matchId}")
    @Operation(summary = "O'yin haqida batafsil")
    public ResponseEntity<ApiResponse<MatchResponse>> getMatch(@PathVariable UUID matchId) {
        return ResponseEntity.ok(ApiResponse.ok(matchService.getMatch(matchId)));
    }

    @PostMapping
    @Operation(summary = "Yangi o'yin yaratish")
    public ResponseEntity<ApiResponse<MatchResponse>> createMatch(
            @Valid @RequestBody MatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(matchService.createMatch(request)));
    }

    @PutMapping("/{matchId}/result")
    @Operation(summary = "O'yin natijasini kiritish")
    public ResponseEntity<ApiResponse<MatchResponse>> updateResult(
            @PathVariable UUID matchId,
            @RequestBody MatchResultRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(matchService.updateResult(matchId, request)));
    }

    @DeleteMapping("/{matchId}")
    @Operation(summary = "O'yinni o'chirish")
    public ResponseEntity<ApiResponse<Void>> deleteMatch(@PathVariable UUID matchId) {
        matchService.deleteMatch(matchId);
        return ResponseEntity.ok(ApiResponse.ok(null, "O'yin o'chirildi"));
    }

    // ===== EVENTS =====

    @PostMapping("/{matchId}/events")
    @Operation(summary = "O'yin voqeasi qo'shish (gol, kartochka va h.k.)")
    public ResponseEntity<ApiResponse<Void>> addEvent(
            @PathVariable UUID matchId,
            @Valid @RequestBody MatchEventRequest request) {
        matchService.addEvent(matchId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null, "Voqea qo'shildi"));
    }

    @PostMapping("/{matchId}/events/batch")
    @Operation(summary = "Bir nechta voqeani birdan qo'shish")
    public ResponseEntity<ApiResponse<Void>> addEvents(
            @PathVariable UUID matchId,
            @Valid @RequestBody List<MatchEventRequest> requests) {
        matchService.addEvents(matchId, requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null, requests.size() + " ta voqea qo'shildi"));
    }

    @GetMapping("/{matchId}/events")
    @Operation(summary = "O'yin voqealari ro'yxati")
    public ResponseEntity<ApiResponse<List<MatchEvent>>> getEvents(@PathVariable UUID matchId) {
        return ResponseEntity.ok(ApiResponse.ok(matchService.getEvents(matchId)));
    }

    // ===== STATS =====

    @PostMapping("/{matchId}/stats")
    @Operation(summary = "O'yin statistikasini kiritish")
    public ResponseEntity<ApiResponse<Void>> saveStats(
            @PathVariable UUID matchId,
            @RequestBody MatchStatsRequest request) {
        matchService.saveStats(matchId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Statistika saqlandi"));
    }
}
