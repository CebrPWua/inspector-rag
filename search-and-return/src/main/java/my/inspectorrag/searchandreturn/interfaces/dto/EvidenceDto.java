package my.inspectorrag.searchandreturn.interfaces.dto;

public record EvidenceDto(
        int citeNo,
        Long chunkId,
        String lawName,
        String articleNo,
        String quotedText,
        Double score
) {
}
