package uz.footballai.ai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, UUID> {

    Page<AnalysisReport> findByOurTeamClubIdAndDeletedFalse(UUID clubId, Pageable pageable);

    List<AnalysisReport> findByOpponentIdAndStatusAndDeletedFalse(UUID opponentId, AnalysisStatus status);

    Optional<AnalysisReport> findByIdAndOurTeamClubIdAndDeletedFalse(UUID id, UUID clubId);
}
