package my.inspectorrag.records.interfaces.dto;

public record QaReplayEvidenceDto(
        Integer citeNo,
        String chunkId,
        String lawName,
        String articleNo,
        String quotedText
) {
}
