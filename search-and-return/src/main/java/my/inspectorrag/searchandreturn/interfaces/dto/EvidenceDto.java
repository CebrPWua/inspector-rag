package my.inspectorrag.searchandreturn.interfaces.dto;

public record EvidenceDto(
        int citeNo,
        String chunkId,
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
