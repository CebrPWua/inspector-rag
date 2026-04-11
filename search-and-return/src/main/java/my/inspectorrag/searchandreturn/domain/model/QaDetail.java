package my.inspectorrag.searchandreturn.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record QaDetail(
        Long qaId,
        String question,
        String normalizedQuestion,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt,
        List<QaEvidence> evidences
) {
}
