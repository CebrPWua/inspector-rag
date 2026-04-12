package my.inspectorrag.taskscheduling.domain.model;

import java.time.OffsetDateTime;

public record DeadLetterTask(
        Long id,
        Long taskId,
        Long docId,
        String taskType,
        String lastError,
        String resolutionStatus,
        String assignedTo,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
