package uz.footballai.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findByTeamIdAndDeletedFalse(UUID teamId);

    Optional<Player> findByIdAndDeletedFalse(UUID id);

    List<Player> findByTeamIdAndPositionAndDeletedFalse(UUID teamId, Position position);

    long countByTeamIdAndDeletedFalse(UUID teamId);
}
