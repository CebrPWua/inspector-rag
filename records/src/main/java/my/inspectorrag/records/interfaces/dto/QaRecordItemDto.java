package my.inspectorrag.records.interfaces.dto;

import java.time.OffsetDateTime;

public record QaRecordItemDto(
        Long qaId,
        String question,
        String answerStatus,
        Integer elapsedMs,
        OffsetDateTime createdAt
) {
}
