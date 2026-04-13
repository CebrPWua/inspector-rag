package my.inspectorrag.records.interfaces.dto;

public record QaReplayCandidateDto(
        String chunkId,
        String sourceType,
        Double rawScore,
        Double finalScore,
        Integer rankNo
) {
}
