package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RejectThresholdConfigRow(
        BigDecimal minTop1Score,
        BigDecimal minTop1ScoreVectorOnly,
        BigDecimal minTopGap,
        BigDecimal minConfidentScore,
        Integer minEvidenceCount,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
