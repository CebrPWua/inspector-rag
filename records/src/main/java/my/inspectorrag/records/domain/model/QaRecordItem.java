package my.inspectorrag.records.domain.model;

import java.time.OffsetDateTime;

public record QaRecordItem(
        Long qaId,
        String question,
        String answerStatus,
        Integer elapsedMs,
        OffsetDateTime createdAt
) {
}
