package uz.footballai.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByClubIdAndDeletedFalse(UUID clubId);

    Optional<Team> findByIdAndClubIdAndDeletedFalse(UUID id, UUID clubId);

    long countByClubIdAndDeletedFalse(UUID clubId);

    Optional<Team> findFirstByClubIdAndNameIgnoreCaseAndDeletedFalse(UUID clubId, String name);
}
