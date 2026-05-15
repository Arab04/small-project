package uz.footballai.player;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.player.dto.PlayerRequest;
import uz.footballai.player.dto.PlayerResponse;
import uz.footballai.team.Team;
import uz.footballai.team.TeamService;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamService teamService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<PlayerResponse> getPlayersByTeam(UUID teamId) {
        User user = userService.getCurrentUser();
        teamService.findByIdAndClub(teamId, user.getClub().getId()); // access check
        return playerRepository.findByTeamIdAndDeletedFalse(teamId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlayerResponse createPlayer(UUID teamId, PlayerRequest request) {
        User user = userService.getCurrentUser();
        Team team = teamService.findByIdAndClub(teamId, user.getClub().getId());

        Player player = Player.builder()
                .team(team)
                .fullName(request.getFullName())
                .jerseyNumber(request.getJerseyNumber())
                .position(request.getPosition())
                .birthDate(request.getBirthDate())
                .height(request.getHeight())
                .weight(request.getWeight())
                .preferredFoot(request.getPreferredFoot())
                .nationality(request.getNationality())
                .strengths(request.getStrengths())
                .weaknesses(request.getWeaknesses())
                .notes(request.getNotes())
                .build();

        player = playerRepository.save(player);
        return toResponse(player);
    }

    @Transactional
    public PlayerResponse updatePlayer(UUID playerId, PlayerRequest request) {
        Player player = playerRepository.findByIdAndDeletedFalse(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        // Club access check
        User user = userService.getCurrentUser();
        if (!player.getTeam().getClub().getId().equals(user.getClub().getId())) {
            throw new ResourceNotFoundException("Player", "id", playerId);
        }

        if (request.getFullName() != null) player.setFullName(request.getFullName());
        if (request.getJerseyNumber() != null) player.setJerseyNumber(request.getJerseyNumber());
        if (request.getPosition() != null) player.setPosition(request.getPosition());
        if (request.getBirthDate() != null) player.setBirthDate(request.getBirthDate());
        if (request.getHeight() != null) player.setHeight(request.getHeight());
        if (request.getWeight() != null) player.setWeight(request.getWeight());
        if (request.getPreferredFoot() != null) player.setPreferredFoot(request.getPreferredFoot());
        if (request.getNationality() != null) player.setNationality(request.getNationality());
        if (request.getStrengths() != null) player.setStrengths(request.getStrengths());
        if (request.getWeaknesses() != null) player.setWeaknesses(request.getWeaknesses());
        if (request.getNotes() != null) player.setNotes(request.getNotes());

        player = playerRepository.save(player);
        return toResponse(player);
    }

    @Transactional
    public void deletePlayer(UUID playerId) {
        Player player = playerRepository.findByIdAndDeletedFalse(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        User user = userService.getCurrentUser();
        if (!player.getTeam().getClub().getId().equals(user.getClub().getId())) {
            throw new ResourceNotFoundException("Player", "id", playerId);
        }

        player.softDelete();
        playerRepository.save(player);
    }

    private PlayerResponse toResponse(Player player) {
        return PlayerResponse.builder()
                .id(player.getId())
                .fullName(player.getFullName())
                .jerseyNumber(player.getJerseyNumber())
                .position(player.getPosition().name())
                .birthDate(player.getBirthDate())
                .height(player.getHeight())
                .weight(player.getWeight())
                .preferredFoot(player.getPreferredFoot())
                .nationality(player.getNationality())
                .photoUrl(player.getPhotoUrl())
                .strengths(player.getStrengths())
                .weaknesses(player.getWeaknesses())
                .notes(player.getNotes())
                .build();
    }
}
