package uz.footballai.opponent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpponentRepository extends JpaRepository<Opponent, UUID> {

    Page<Opponent> findByClubIdAndDeletedFalse(UUID clubId, Pageable pageable);

    Optional<Opponent> findByIdAndClubIdAndDeletedFalse(UUID id, UUID clubId);

    long countByClubIdAndDeletedFalse(UUID clubId);

    Optional<Opponent> findFirstByClubIdAndNameIgnoreCaseAndDeletedFalse(UUID clubId, String name);
}
