package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import java.math.BigDecimal;

public record QaEvidenceRow(
        Integer citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText,
        String sourceType,
        BigDecimal finalScore,
        Integer pageStart,
        Integer pageEnd,
        String fileVersion
) {
}
