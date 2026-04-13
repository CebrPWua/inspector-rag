package my.inspectorrag.records.interfaces.dto;

import java.time.OffsetDateTime;

public record QaRecordItemDto(
        String qaId,
        String question,
        String answerStatus,
        Integer elapsedMs,
        OffsetDateTime createdAt
) {
}
