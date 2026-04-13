package my.inspectorrag.searchandreturn.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record ConversationMessage(
        Long qaId,
        Integer turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        List<String> rewriteQueries,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt,
        List<QaEvidence> evidences
) {
}
