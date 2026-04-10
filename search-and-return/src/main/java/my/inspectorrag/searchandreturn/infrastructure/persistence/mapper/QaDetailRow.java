package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record QaDetailRow(
        Long qaId,
        String question,
        String normalizedQuestion,
        String answer,
        String answerStatus,
        OffsetDateTime createdAt
) {
}
