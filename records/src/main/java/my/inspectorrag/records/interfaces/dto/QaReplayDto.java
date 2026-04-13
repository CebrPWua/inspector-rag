package my.inspectorrag.records.interfaces.dto;

import java.util.List;

public record QaReplayDto(
        String qaId,
        String conversationId,
        int turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        List<String> rewriteQueries,
        String answer,
        List<QaReplayCandidateDto> candidates,
        List<QaReplayEvidenceDto> evidences
) {
}
