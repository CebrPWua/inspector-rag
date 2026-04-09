package my.inspectorrag.records.domain.model;

import java.util.List;

public record QaReplay(
        Long qaId,
        String question,
        String normalizedQuestion,
        String answer,
        List<QaReplayCandidate> candidates,
        List<QaReplayEvidence> evidences
) {
}
