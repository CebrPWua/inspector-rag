package my.inspectorrag.searchandreturn.domain.model;

public record QaEvidence(
        Integer citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText,
        String sourceType,
        Double finalScore,
        Integer pageStart,
        Integer pageEnd,
        String fileVersion
) {
}
