package my.inspectorrag.searchandreturn.domain.service;

import my.inspectorrag.searchandreturn.domain.model.RewriteResult;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;

import java.util.List;

public interface QuestionRewriteService {

    RewriteResult rewrite(String originalQuestion, String normalizedQuestion, List<ConversationContextTurn> contextTurns);
}
