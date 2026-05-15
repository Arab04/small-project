package uz.footballai.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.club.Club;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.team.dto.TeamRequest;
import uz.footballai.team.dto.TeamResponse;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams() {
        User user = userService.getCurrentUser();
        return teamRepository.findByClubIdAndDeletedFalse(user.getClub().getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamResponse createTeam(TeamRequest request) {
        User user = userService.getCurrentUser();
        Club club = user.getClub();

        if (!club.canCreateTeam()) {
            throw new BusinessException(
                    "Jamoalar soni limitdan oshib ketdi. Tarifingiz: " + club.getSubscriptionPlan().name() +
                    ", limit: " + club.getMaxTeams());
        }

        Team team = Team.builder()
                .club(club)
                .name(request.getName())
                .ageCategory(request.getAgeCategory())
                .league(request.getLeague())
                .typicalFormation(request.getTypicalFormation())
                .build();

        team = teamRepository.save(team);
        return toResponse(team);
    }

    @Transactional
    public TeamResponse updateTeam(UUID teamId, TeamRequest request) {
        User user = userService.getCurrentUser();
        Team team = teamRepository.findByIdAndClubIdAndDeletedFalse(teamId, user.getClub().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));

        if (request.getName() != null) team.setName(request.getName());
        if (request.getAgeCategory() != null) team.setAgeCategory(request.getAgeCategory());
        if (request.getLeague() != null) team.setLeague(request.getLeague());
        if (request.getTypicalFormation() != null) team.setTypicalFormation(request.getTypicalFormation());

        team = teamRepository.save(team);
        return toResponse(team);
    }

    @Transactional
    public void deleteTeam(UUID teamId) {
        User user = userService.getCurrentUser();
        Team team = teamRepository.findByIdAndClubIdAndDeletedFalse(teamId, user.getClub().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        team.softDelete();
        teamRepository.save(team);
    }

    @Transactional(readOnly = true)
    public Team findByIdAndClub(UUID teamId, UUID clubId) {
        return teamRepository.findByIdAndClubIdAndDeletedFalse(teamId, clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    private TeamResponse toResponse(Team team) {
        long playerCount = team.getPlayers() != null
                ? team.getPlayers().stream().filter(p -> !p.isDeleted()).count()
                : 0;

        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .ageCategory(team.getAgeCategory())
                .league(team.getLeague())
                .typicalFormation(team.getTypicalFormation())
                .playerCount((int) playerCount)
                .build();
    }

    /**
     * Find a team by name within the current user's club; create one if it doesn't exist.
     */
    @Transactional
    public Team findOrCreateByName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Jamoa nomi kiritilishi shart");
        }
        User user = userService.getCurrentUser();
        Club club = user.getClub();
        String trimmed = name.trim();
        return teamRepository
                .findFirstByClubIdAndNameIgnoreCaseAndDeletedFalse(club.getId(), trimmed)
                .orElseGet(() -> {
                    Team team = Team.builder()
                            .club(club)
                            .name(trimmed)
                            .build();
                    return teamRepository.save(team);
                });
    }

}
