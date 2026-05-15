package uz.footballai.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    Page<Match> findByClubIdAndDeletedFalse(UUID clubId, Pageable pageable);

    Optional<Match> findByIdAndClubIdAndDeletedFalse(UUID id, UUID clubId);

    List<Match> findByOpponentIdAndDeletedFalseOrderByMatchDateDesc(UUID opponentId);

    @Query("SELECT m FROM Match m WHERE m.opponent.id = :opponentId AND m.deleted = false " +
           "AND m.status IN ('FINISHED', 'ANALYZED') ORDER BY m.matchDate DESC")
    List<Match> findFinishedByOpponentId(@Param("opponentId") UUID opponentId);

    @Query("SELECT m FROM Match m WHERE m.ourTeam.id = :teamId AND m.deleted = false " +
           "AND m.status IN ('FINISHED', 'ANALYZED') ORDER BY m.matchDate DESC")
    List<Match> findFinishedByOurTeamId(@Param("teamId") UUID teamId);

    long countByClubIdAndDeletedFalse(UUID clubId);
}
