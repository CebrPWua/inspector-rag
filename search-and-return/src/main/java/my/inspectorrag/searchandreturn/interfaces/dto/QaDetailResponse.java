package my.inspectorrag.searchandreturn.interfaces.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record QaDetailResponse(
        String qaId,
        String conversationId,
        int turnNo,
        String question,
        String normalizedQuestion,
        String rewrittenQuestion,
        List<String> rewriteQueries,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt,
        List<EvidenceDto> evidences
) {
}
