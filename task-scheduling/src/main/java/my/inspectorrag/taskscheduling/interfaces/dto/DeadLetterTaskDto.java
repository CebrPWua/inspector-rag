package my.inspectorrag.taskscheduling.interfaces.dto;

import java.time.OffsetDateTime;

public record DeadLetterTaskDto(
        Long id,
        Long taskId,
        Long docId,
        String taskType,
        String lastError,
        String resolutionStatus,
        OffsetDateTime createdAt
) {
}
