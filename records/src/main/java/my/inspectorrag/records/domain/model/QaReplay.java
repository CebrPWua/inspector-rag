package my.inspectorrag.records.domain.model;

import java.util.List;

public record QaReplay(
        Long qaId,
        Long conversationId,
        Integer turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        List<String> rewriteQueries,
        String answer,
        List<QaReplayCandidate> candidates,
        List<QaReplayEvidence> evidences
) {
}
