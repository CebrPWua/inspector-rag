package my.inspectorrag.records.interfaces.dto;

public record QaReplayEvidenceDto(
        Integer citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText
) {
}
