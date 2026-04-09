package my.inspectorrag.records.domain.model;

public record QaReplayCandidate(
        Long chunkId,
        String sourceType,
        Double rawScore,
        Double finalScore,
        Integer rankNo
) {
}
