package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import java.math.BigDecimal;

public record QaEvidenceRow(
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText,
        BigDecimal finalScore,
        Integer pageStart,
        Integer pageEnd,
        String fileVersion
) {
}
