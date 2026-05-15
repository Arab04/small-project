package uz.footballai.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

    List<MatchEvent> findByMatchIdAndDeletedFalseOrderByMinuteAsc(UUID matchId);

    List<MatchEvent> findByMatchIdAndTypeAndDeletedFalse(UUID matchId, MatchEventType type);

    long countByMatchIdAndDeletedFalse(UUID matchId);
}
