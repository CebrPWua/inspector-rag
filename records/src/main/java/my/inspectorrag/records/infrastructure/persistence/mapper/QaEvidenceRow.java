package my.inspectorrag.records.infrastructure.persistence.mapper;

public record QaEvidenceRow(
        Integer citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText
) {
}
