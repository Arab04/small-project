package uz.footballai.match;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.footballai.common.PageResponse;
import uz.footballai.common.exception.BusinessException;
import uz.footballai.common.exception.ResourceNotFoundException;
import uz.footballai.match.dto.*;
import uz.footballai.opponent.Opponent;
import uz.footballai.opponent.OpponentService;
import uz.footballai.team.Team;
import uz.footballai.team.TeamService;
import uz.footballai.user.User;
import uz.footballai.user.UserService;
import uz.footballai.video.VideoAnalysisJobRepository;
import uz.footballai.video.VideoJobStatus;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final MatchStatsRepository matchStatsRepository;
    private final VideoAnalysisJobRepository videoAnalysisJobRepository;
    private final UserService userService;
    private final TeamService teamService;
    private final OpponentService opponentService;

    // ===== MATCH CRUD =====

    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> getMatches(Pageable pageable) {
        User user = userService.getCurrentUser();
        Page<MatchResponse> page = matchRepository
                .findByClubIdAndDeletedFalse(user.getClub().getId(), pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID matchId) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());
        return toResponse(match);
    }

    @Transactional
    public MatchResponse createMatch(MatchCreateRequest request) {
        User user = userService.getCurrentUser();
        UUID clubId = user.getClub().getId();

        Team ourTeam;
        Opponent opponent;
        java.time.LocalDate matchDate;
        boolean isHome;
        String competition;
        Integer ourScore = null;
        Integer opponentScore = null;

        if (request.getOurTeamId() != null && request.getOpponentId() != null) {
            // Eski format: UUID asosida
            ourTeam = teamService.findByIdAndClub(request.getOurTeamId(), clubId);
            opponent = opponentService.findByIdAndClub(request.getOpponentId(), clubId);
            matchDate = request.getMatchDate();
            isHome = request.isHome();
            competition = request.getCompetition();
        } else if (request.getHomeTeamName() != null && request.getAwayTeamName() != null) {
            // Yangi format: nomlar asosida (auto-create)
            ourTeam = teamService.findOrCreateByName(request.getHomeTeamName());
            opponent = opponentService.findOrCreateByName(request.getAwayTeamName());
            matchDate = request.getKickoffAt() != null
                    ? request.getKickoffAt().toLocalDate()
                    : java.time.LocalDate.now();
            isHome = true;
            competition = request.getLeague();
            ourScore = request.getHomeScore();
            opponentScore = request.getAwayScore();
        } else {
            throw new BusinessException(
                    "O'yin yaratish uchun ourTeamId+opponentId yoki homeTeamName+awayTeamName kiritilishi kerak");
        }

        Match match = Match.builder()
                .club(user.getClub())
                .ourTeam(ourTeam)
                .opponent(opponent)
                .isHome(isHome)
                .matchDate(matchDate)
                .venue(request.getVenue())
                .competition(competition)
                .ourFormation(request.getOurFormation())
                .opponentFormation(request.getOpponentFormation())
                .status(MatchStatus.SCHEDULED)
                .notes(request.getNotes())
                .build();

        if (ourScore != null) match.setOurScore(ourScore);
        if (opponentScore != null) match.setOpponentScore(opponentScore);
        if (ourScore != null && opponentScore != null) {
            match.setStatus(MatchStatus.FINISHED);
        }

        match = matchRepository.save(match);
        log.info("O'yin yaratildi: {} vs {} ({})", ourTeam.getName(), opponent.getName(), match.getMatchDate());
        return toResponse(match);
    }

    @Transactional
    public MatchResponse updateResult(UUID matchId, MatchResultRequest request) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());

        if (request.getOurScore() != null) match.setOurScore(request.getOurScore());
        if (request.getOpponentScore() != null) match.setOpponentScore(request.getOpponentScore());
        if (request.getOurFormation() != null) match.setOurFormation(request.getOurFormation());
        if (request.getOpponentFormation() != null) match.setOpponentFormation(request.getOpponentFormation());
        if (request.getNotes() != null) match.setNotes(request.getNotes());

        if (match.getOurScore() != null && match.getOpponentScore() != null) {
            match.setStatus(MatchStatus.FINISHED);
        }

        match = matchRepository.save(match);
        return toResponse(match);
    }

    @Transactional
    public void deleteMatch(UUID matchId) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());
        match.softDelete();
        matchRepository.save(match);
    }

    // ===== MATCH EVENTS =====

    @Transactional
    public void addEvent(UUID matchId, MatchEventRequest request) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());

        MatchEvent event = MatchEvent.builder()
                .match(match)
                .minute(request.getMinute())
                .additionalMinute(request.getAdditionalMinute())
                .type(request.getType())
                .teamSide(request.getTeamSide())
                .playerName(request.getPlayerName())
                .secondPlayerName(request.getSecondPlayerName())
                .description(request.getDescription())
                .xCoordinate(request.getXCoordinate())
                .yCoordinate(request.getYCoordinate())
                .build();

        matchEventRepository.save(event);
    }

    @Transactional
    public void addEvents(UUID matchId, List<MatchEventRequest> requests) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());

        List<MatchEvent> events = requests.stream()
                .map(req -> MatchEvent.builder()
                        .match(match)
                        .minute(req.getMinute())
                        .additionalMinute(req.getAdditionalMinute())
                        .type(req.getType())
                        .teamSide(req.getTeamSide())
                        .playerName(req.getPlayerName())
                        .secondPlayerName(req.getSecondPlayerName())
                        .description(req.getDescription())
                        .xCoordinate(req.getXCoordinate())
                        .yCoordinate(req.getYCoordinate())
                        .build())
                .collect(Collectors.toList());

        matchEventRepository.saveAll(events);
    }

    @Transactional(readOnly = true)
    public List<MatchEvent> getEvents(UUID matchId) {
        return matchEventRepository.findByMatchIdAndDeletedFalseOrderByMinuteAsc(matchId);
    }

    // ===== MATCH STATS =====

    @Transactional
    public void saveStats(UUID matchId, MatchStatsRequest request) {
        User user = userService.getCurrentUser();
        Match match = findByIdAndClub(matchId, user.getClub().getId());

        MatchStats stats = matchStatsRepository.findByMatchId(matchId)
                .orElse(MatchStats.builder().match(match).build());

        stats.setOurPossession(request.getOurPossession());
        stats.setOurTotalShots(request.getOurTotalShots());
        stats.setOurShotsOnTarget(request.getOurShotsOnTarget());
        stats.setOurCorners(request.getOurCorners());
        stats.setOurFouls(request.getOurFouls());
        stats.setOurOffsides(request.getOurOffsides());
        stats.setOurPassAccuracy(request.getOurPassAccuracy());
        stats.setOurYellowCards(request.getOurYellowCards());
        stats.setOurRedCards(request.getOurRedCards());
        stats.setOurSaves(request.getOurSaves());
        stats.setOurXg(request.getOurXg());

        stats.setOppPossession(request.getOppPossession());
        stats.setOppTotalShots(request.getOppTotalShots());
        stats.setOppShotsOnTarget(request.getOppShotsOnTarget());
        stats.setOppCorners(request.getOppCorners());
        stats.setOppFouls(request.getOppFouls());
        stats.setOppOffsides(request.getOppOffsides());
        stats.setOppPassAccuracy(request.getOppPassAccuracy());
        stats.setOppYellowCards(request.getOppYellowCards());
        stats.setOppRedCards(request.getOppRedCards());
        stats.setOppSaves(request.getOppSaves());
        stats.setOppXg(request.getOppXg());

        stats.setOurDuelsWon(request.getOurDuelsWon());
        stats.setOppDuelsWon(request.getOppDuelsWon());
        stats.setOurAerialDuelsWon(request.getOurAerialDuelsWon());
        stats.setOppAerialDuelsWon(request.getOppAerialDuelsWon());
        stats.setOurTackles(request.getOurTackles());
        stats.setOppTackles(request.getOppTackles());
        stats.setOurInterceptions(request.getOurInterceptions());
        stats.setOppInterceptions(request.getOppInterceptions());
        stats.setOurDistanceCovered(request.getOurDistanceCovered());
        stats.setOppDistanceCovered(request.getOppDistanceCovered());

        matchStatsRepository.save(stats);
    }

    // ===== HELPERS =====

    public Match findByIdAndClub(UUID matchId, UUID clubId) {
        return matchRepository.findByIdAndClubIdAndDeletedFalse(matchId, clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
    }

    /**
     * Video URL saqlash (VideoUploadController uchun).
     */
    @Transactional
    public void setVideoUrl(UUID matchId, String videoUrl) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
        match.setVideoUrl(videoUrl);
        matchRepository.save(match);
    }

    /**
     * Event'larni user context'siz qo'shish
     * (ML servis callback - async jarayondan chaqiriladi).
     */
    @Transactional
    public void addEventsForMatch(UUID matchId, List<MatchEventRequest> requests) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));

        List<MatchEvent> events = requests.stream()
                .map(req -> MatchEvent.builder()
                        .match(match)
                        .minute(req.getMinute())
                        .additionalMinute(req.getAdditionalMinute())
                        .type(req.getType())
                        .teamSide(req.getTeamSide())
                        .playerName(req.getPlayerName())
                        .secondPlayerName(req.getSecondPlayerName())
                        .description(req.getDescription())
                        .xCoordinate(req.getXCoordinate())
                        .yCoordinate(req.getYCoordinate())
                        .build())
                .collect(Collectors.toList());

        matchEventRepository.saveAll(events);
    }

    public List<Match> getFinishedMatchesByOpponent(UUID opponentId) {
        return matchRepository.findFinishedByOpponentId(opponentId);
    }

    public List<Match> getFinishedMatchesByTeam(UUID teamId) {
        return matchRepository.findFinishedByOurTeamId(teamId);
    }

    private MatchResponse toResponse(Match match) {
        return MatchResponse.builder()
                .id(match.getId())
                .ourTeamId(match.getOurTeam().getId())
                .ourTeamName(match.getOurTeam().getName())
                .opponentId(match.getOpponent().getId())
                .opponentName(match.getOpponent().getName())
                .isHome(match.isHome())
                .matchDate(match.getMatchDate())
                .venue(match.getVenue())
                .competition(match.getCompetition())
                .ourScore(match.getOurScore())
                .opponentScore(match.getOpponentScore())
                .result(match.getResult())
                .scoreDisplay(match.getScoreDisplay())
                .ourFormation(match.getOurFormation())
                .opponentFormation(match.getOpponentFormation())
                .status(match.getStatus().name())
                .videoUrl(match.getVideoUrl())
                .notes(match.getNotes())
                .eventCount((int) matchEventRepository.countByMatchIdAndDeletedFalse(match.getId()))
                .hasStats(matchStatsRepository.existsByMatchId(match.getId()))
                .videoUploaded(match.getVideoUrl() != null && !match.getVideoUrl().isBlank())
                .analyzed(videoAnalysisJobRepository
                        .findFirstByMatchIdAndStatusOrderByCreatedAtDesc(match.getId(), VideoJobStatus.COMPLETED)
                        .isPresent())
                .build();
    }
}
