package my.inspectorrag.searchandreturn.interfaces.dto;

import java.time.OffsetDateTime;

public record RejectThresholdConfigResponse(
        double minTop1Score,
        double minTop1ScoreVectorOnly,
        double minTopGap,
        double minConfidentScore,
        int minEvidenceCount,
        String updatedBy,
        OffsetDateTime updatedAt,
        String source
) {
}
