package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

public record RecallCandidateRow(
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
