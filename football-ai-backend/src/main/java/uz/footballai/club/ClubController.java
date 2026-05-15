package uz.footballai.club;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.club.dto.ClubResponse;
import uz.footballai.club.dto.ClubUpdateRequest;
import uz.footballai.common.ApiResponse;

@RestController
@RequestMapping("/clubs")
@RequiredArgsConstructor
@Tag(name = "Clubs", description = "Klub boshqaruvi")
public class ClubController {

    private final ClubService clubService;

    @GetMapping("/me")
    @Operation(summary = "O'z klubim ma'lumotlarini olish")
    public ResponseEntity<ApiResponse<ClubResponse>> getMyClub() {
        return ResponseEntity.ok(ApiResponse.ok(clubService.getMyClub()));
    }

    @PutMapping("/me")
    @Operation(summary = "O'z klubim ma'lumotlarini yangilash")
    public ResponseEntity<ApiResponse<ClubResponse>> updateMyClub(@RequestBody ClubUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(clubService.updateMyClub(request)));
    }
}
