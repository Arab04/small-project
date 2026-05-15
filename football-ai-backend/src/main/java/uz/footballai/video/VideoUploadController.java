package uz.footballai.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.footballai.common.ApiResponse;
import uz.footballai.match.Match;
import uz.footballai.match.MatchService;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/matches/{matchId}/video")
@RequiredArgsConstructor
@Tag(name = "Video", description = "O'yin videolari boshqaruvi")
public class VideoUploadController {

    private final VideoStorageService videoStorageService;
    private final MatchService matchService;
    private final UserService userService;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "O'yin videosini yuklash (max 500MB)")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadVideo(
            @PathVariable UUID matchId,
            @RequestParam("file") MultipartFile file) {

        User user = userService.getCurrentUser();
        Match match = matchService.findByIdAndClub(matchId, user.getClub().getId());

        String objectName = videoStorageService.uploadVideo(file, matchId);

        // Match'ga video URL saqlash
        matchService.setVideoUrl(matchId, objectName);

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("videoKey", objectName,
                       "message", "Video muvaffaqiyatli yuklandi"),
                "Video yuklandi"
        ));
    }

    @GetMapping("/url")
    @Operation(summary = "Video ko'rish uchun vaqtinchalik havola olish (2 soatga)")
    public ResponseEntity<ApiResponse<Map<String, String>>> getVideoUrl(@PathVariable UUID matchId) {
        User user = userService.getCurrentUser();
        Match match = matchService.findByIdAndClub(matchId, user.getClub().getId());

        if (match.getVideoUrl() == null) {
            return ResponseEntity.ok(ApiResponse.error("Bu o'yinga video yuklanmagan"));
        }

        String presignedUrl = videoStorageService.getPresignedUrl(match.getVideoUrl());
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("url", presignedUrl)
        ));
    }
}
