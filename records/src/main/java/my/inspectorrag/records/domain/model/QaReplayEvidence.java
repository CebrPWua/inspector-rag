package my.inspectorrag.records.domain.model;

public record QaReplayEvidence(
        Integer citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText
) {
}
