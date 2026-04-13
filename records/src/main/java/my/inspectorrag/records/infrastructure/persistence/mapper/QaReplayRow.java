package my.inspectorrag.records.infrastructure.persistence.mapper;

public record QaReplayRow(
        Long qaId,
        Long conversationId,
        Integer turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        String rewriteQueriesJson,
        String answer
) {
}
