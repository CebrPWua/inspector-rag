package my.inspectorrag.searchandreturn.domain.service;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;

import java.util.List;

public interface AnswerGenerator {

    String generate(
            String originalQuestion,
            String effectiveRewrittenQuestion,
            List<ConversationContextTurn> contextTurns,
            List<RecallCandidate> candidates
    );

    String generateLowConfidenceGuidance(
            String originalQuestion,
            String effectiveRewrittenQuestion,
            List<ConversationContextTurn> contextTurns
    );
}
