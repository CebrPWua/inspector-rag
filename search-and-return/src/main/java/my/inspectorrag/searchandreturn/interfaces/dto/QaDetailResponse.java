package my.inspectorrag.searchandreturn.interfaces.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record QaDetailResponse(
        String qaId,
        String question,
        String normalizedQuestion,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt,
        List<EvidenceDto> evidences
) {
}
