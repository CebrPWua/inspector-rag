package my.inspectorrag.records.infrastructure.persistence.mapper;

import java.math.BigDecimal;

public record QaCandidateRow(
        Long chunkId,
        String sourceType,
        BigDecimal rawScore,
        BigDecimal finalScore,
        Integer rankNo
) {
}
