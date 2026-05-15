package uz.footballai.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.footballai.common.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Foydalanuvchi profili")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Joriy foydalanuvchi profili")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        User user = userService.getCurrentUser();
        Map<String, Object> profile = Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : "",
                "role", user.getRole().name(),
                "clubId", user.getClub() != null ? user.getClub().getId() : "",
                "clubName", user.getClub() != null ? user.getClub().getName() : ""
        );
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
