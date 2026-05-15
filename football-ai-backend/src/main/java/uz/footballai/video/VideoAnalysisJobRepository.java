package uz.footballai.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoAnalysisJobRepository extends JpaRepository<VideoAnalysisJob, UUID> {

    Optional<VideoAnalysisJob> findByMlJobId(String mlJobId);

    List<VideoAnalysisJob> findByMatchIdAndDeletedFalseOrderByCreatedAtDesc(UUID matchId);

    Optional<VideoAnalysisJob> findFirstByMatchIdAndStatusOrderByCreatedAtDesc(UUID matchId, VideoJobStatus status);
}
