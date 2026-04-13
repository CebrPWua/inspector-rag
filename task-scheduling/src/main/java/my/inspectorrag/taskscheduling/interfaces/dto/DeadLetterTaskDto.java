package my.inspectorrag.taskscheduling.interfaces.dto;

import java.time.OffsetDateTime;

public record DeadLetterTaskDto(
        String id,
        String taskId,
        String docId,
        String taskType,
        String lastError,
        String resolutionStatus,
        String assignedTo,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
