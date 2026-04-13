package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

public record ConversationContextRow(
        String question,
        String rewrittenQuestion,
        String answer,
        String answerStatus
) {
}
