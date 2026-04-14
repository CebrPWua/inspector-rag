package my.inspectorrag.searchandreturn.domain.model;

import java.time.OffsetDateTime;

public record RejectThresholdConfig(
        double minTop1Score,
        double minTop1ScoreVectorOnly,
        double minTopGap,
        double minConfidentScore,
        int minEvidenceCount,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
