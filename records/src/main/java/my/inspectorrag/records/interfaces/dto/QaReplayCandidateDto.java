package my.inspectorrag.records.interfaces.dto;

public record QaReplayCandidateDto(
        Long chunkId,
        String sourceType,
        Double rawScore,
        Double finalScore,
        Integer rankNo
) {
}
