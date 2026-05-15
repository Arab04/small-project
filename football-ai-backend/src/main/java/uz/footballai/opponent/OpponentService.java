package uz.footballai.opponent;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.common.PageResponse;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.opponent.dto.OpponentRequest;
import uz.footballai.opponent.dto.OpponentResponse;
import uz.footballai.user.User;
import uz.footballai.user.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpponentService {

    private final OpponentRepository opponentRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public PageResponse<OpponentResponse> getOpponents(Pageable pageable) {
        User user = userService.getCurrentUser();
        Page<OpponentResponse> page = opponentRepository
                .findByClubIdAndDeletedFalse(user.getClub().getId(), pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public OpponentResponse getOpponent(UUID opponentId) {
        User user = userService.getCurrentUser();
        Opponent opponent = findByIdAndClub(opponentId, user.getClub().getId());
        return toResponse(opponent);
    }

    @Transactional
    public OpponentResponse createOpponent(OpponentRequest request) {
        User user = userService.getCurrentUser();

        Opponent opponent = Opponent.builder()
                .club(user.getClub())
                .name(request.getName())
                .league(request.getLeague())
                .city(request.getCity())
                .typicalFormation(request.getTypicalFormation())
                .coachName(request.getCoachName())
                .strengths(request.getStrengths())
                .weaknesses(request.getWeaknesses())
                .playStyle(request.getPlayStyle())
                .keyPlayers(request.getKeyPlayers())
                .notes(request.getNotes())
                .build();

        opponent = opponentRepository.save(opponent);
        return toResponse(opponent);
    }

    @Transactional
    public OpponentResponse updateOpponent(UUID opponentId, OpponentRequest request) {
        User user = userService.getCurrentUser();
        Opponent opponent = findByIdAndClub(opponentId, user.getClub().getId());

        if (request.getName() != null) opponent.setName(request.getName());
        if (request.getLeague() != null) opponent.setLeague(request.getLeague());
        if (request.getCity() != null) opponent.setCity(request.getCity());
        if (request.getTypicalFormation() != null) opponent.setTypicalFormation(request.getTypicalFormation());
        if (request.getCoachName() != null) opponent.setCoachName(request.getCoachName());
        if (request.getStrengths() != null) opponent.setStrengths(request.getStrengths());
        if (request.getWeaknesses() != null) opponent.setWeaknesses(request.getWeaknesses());
        if (request.getPlayStyle() != null) opponent.setPlayStyle(request.getPlayStyle());
        if (request.getKeyPlayers() != null) opponent.setKeyPlayers(request.getKeyPlayers());
        if (request.getNotes() != null) opponent.setNotes(request.getNotes());

        opponent = opponentRepository.save(opponent);
        return toResponse(opponent);
    }

    @Transactional
    public void deleteOpponent(UUID opponentId) {
        User user = userService.getCurrentUser();
        Opponent opponent = findByIdAndClub(opponentId, user.getClub().getId());
        opponent.softDelete();
        opponentRepository.save(opponent);
    }

    public Opponent findByIdAndClub(UUID opponentId, UUID clubId) {
        return opponentRepository.findByIdAndClubIdAndDeletedFalse(opponentId, clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Opponent", "id", opponentId));
    }

    private OpponentResponse toResponse(Opponent opponent) {
        return OpponentResponse.builder()
                .id(opponent.getId())
                .name(opponent.getName())
                .league(opponent.getLeague())
                .city(opponent.getCity())
                .typicalFormation(opponent.getTypicalFormation())
                .coachName(opponent.getCoachName())
                .logoUrl(opponent.getLogoUrl())
                .strengths(opponent.getStrengths())
                .weaknesses(opponent.getWeaknesses())
                .playStyle(opponent.getPlayStyle())
                .keyPlayers(opponent.getKeyPlayers())
                .notes(opponent.getNotes())
                .build();
    }

    /**
     * Find an opponent by name within the current user's club; create one if it doesn't exist.
     */
    @Transactional
    public Opponent findOrCreateByName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Raqib nomi kiritilishi shart");
        }
        User user = userService.getCurrentUser();
        String trimmed = name.trim();
        return opponentRepository
                .findFirstByClubIdAndNameIgnoreCaseAndDeletedFalse(user.getClub().getId(), trimmed)
                .orElseGet(() -> {
                    Opponent opp = Opponent.builder()
                            .club(user.getClub())
                            .name(trimmed)
                            .build();
                    return opponentRepository.save(opp);
                });
    }

}
