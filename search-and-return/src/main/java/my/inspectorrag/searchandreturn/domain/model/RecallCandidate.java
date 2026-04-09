package my.inspectorrag.searchandreturn.domain.model;

public record RecallCandidate(
        Long chunkId,
        String lawName,
        String articleNo,
        String content,
        Double score,
        Integer pageStart,
        Integer pageEnd,
        String versionNo
) {
}
