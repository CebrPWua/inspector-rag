package my.inspectorrag.searchandreturn.domain.repository;

import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RejectThresholdConfigRepository {

    Optional<RejectThresholdConfig> findCurrent();

    void upsert(
            double minTop1Score,
            double minTop1ScoreVectorOnly,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount,
            String updatedBy,
            OffsetDateTime now
    );
}
