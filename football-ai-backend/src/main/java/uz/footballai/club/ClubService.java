package uz.footballai.club;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.club.dto.ClubResponse;
import uz.footballai.club.dto.ClubUpdateRequest;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public ClubResponse getMyClub() {
        User currentUser = userService.getCurrentUser();
        Club club = currentUser.getClub();
        if (club == null) {
            throw new ResourceNotFoundException("Sizga biriktirilgan klub yo'q");
        }
        return toResponse(club);
    }

    @Transactional
    public ClubResponse updateMyClub(ClubUpdateRequest request) {
        User currentUser = userService.getCurrentUser();
        Club club = currentUser.getClub();
        if (club == null) {
            throw new ResourceNotFoundException("Sizga biriktirilgan klub yo'q");
        }

        if (request.getName() != null) club.setName(request.getName());
        if (request.getCity() != null) club.setCity(request.getCity());
        if (request.getFoundedYear() != null) club.setFoundedYear(request.getFoundedYear());

        club = clubRepository.save(club);
        return toResponse(club);
    }

    @Transactional(readOnly = true)
    public Club findById(UUID id) {
        return clubRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club", "id", id));
    }

    public ClubResponse toResponse(Club club) {
        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .city(club.getCity())
                .foundedYear(club.getFoundedYear())
                .logoUrl(club.getLogoUrl())
                .subscriptionPlan(club.getSubscriptionPlan().name())
                .maxTeams(club.getMaxTeams())
                .maxAnalysesPerMonth(club.getMaxAnalysesPerMonth())
                .analysesUsedThisMonth(club.getAnalysesUsedThisMonth())
                .build();
    }
}
