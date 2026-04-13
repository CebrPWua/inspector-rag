package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record ConversationMessageRow(
        Long qaId,
        Integer turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        String rewriteQueriesJson,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt
) {
}
