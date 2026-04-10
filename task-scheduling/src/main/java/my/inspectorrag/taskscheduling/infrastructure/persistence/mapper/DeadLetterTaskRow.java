package my.inspectorrag.taskscheduling.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record DeadLetterTaskRow(
        Long id,
        Long taskId,
        Long docId,
        String taskType,
        String lastErrorMsg,
        String resolutionStatus,
        OffsetDateTime createdAt
) {
}
