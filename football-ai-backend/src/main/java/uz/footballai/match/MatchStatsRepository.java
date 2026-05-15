package uz.footballai.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchStatsRepository extends JpaRepository<MatchStats, UUID> {

    Optional<MatchStats> findByMatchId(UUID matchId);

    boolean existsByMatchId(UUID matchId);
}
