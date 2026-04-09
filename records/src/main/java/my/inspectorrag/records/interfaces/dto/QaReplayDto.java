package my.inspectorrag.records.interfaces.dto;

import java.util.List;

public record QaReplayDto(
        Long qaId,
        String question,
        String normalizedQuestion,
        String answer,
        List<QaReplayCandidateDto> candidates,
        List<QaReplayEvidenceDto> evidences
) {
}
