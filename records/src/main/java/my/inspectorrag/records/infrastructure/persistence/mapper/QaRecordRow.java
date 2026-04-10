package my.inspectorrag.records.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record QaRecordRow(
        Long qaId,
        String question,
        String answerStatus,
        Integer elapsedMs,
        OffsetDateTime createdAt
) {
}
