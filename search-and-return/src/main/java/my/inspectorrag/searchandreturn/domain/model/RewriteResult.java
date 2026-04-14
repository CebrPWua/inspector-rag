package my.inspectorrag.searchandreturn.domain.model;

import java.util.List;

public record RewriteResult(
        boolean rewriteNeeded,
        String rewriteReason,
        String rewrittenQuestion,
        List<String> candidateQueries,
        List<String> preserveTerms,
        String reasoningSummary
) {
    public RewriteResult(
            String rewrittenQuestion,
            List<String> candidateQueries,
            String reasoningSummary
    ) {
        this(true, "legacy", rewrittenQuestion, candidateQueries, List.of(), reasoningSummary);
    }
}
