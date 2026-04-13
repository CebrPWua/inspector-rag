package my.inspectorrag.searchandreturn.domain.model;

import java.util.List;

public record RewriteResult(
        String rewrittenQuestion,
        List<String> candidateQueries,
        String reasoningSummary
) {
}
