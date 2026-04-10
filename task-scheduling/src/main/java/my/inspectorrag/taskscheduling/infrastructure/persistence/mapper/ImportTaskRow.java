package my.inspectorrag.taskscheduling.infrastructure.persistence.mapper;

public record ImportTaskRow(
        Long id,
        Long docId,
        String taskType,
        String taskStatus,
        int retryCount,
        int maxRetry
) {
}
