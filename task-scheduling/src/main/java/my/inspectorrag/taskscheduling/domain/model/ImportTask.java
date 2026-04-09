package my.inspectorrag.taskscheduling.domain.model;

public record ImportTask(
        Long id,
        Long docId,
        String taskType,
        String taskStatus,
        int retryCount,
        int maxRetry
) {
}
