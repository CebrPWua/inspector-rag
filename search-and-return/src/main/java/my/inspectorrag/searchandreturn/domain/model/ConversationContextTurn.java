package my.inspectorrag.searchandreturn.domain.model;

public record ConversationContextTurn(
        String question,
        String rewrittenQuestion,
        String answer,
        String answerStatus
) {
}
