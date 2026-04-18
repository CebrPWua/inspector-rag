package my.inspectorrag.embedding.domain.model;

import my.inspectorrag.embedding.domain.model.value.DocumentId;
import my.inspectorrag.embedding.domain.model.value.TaskId;
import my.inspectorrag.embedding.domain.model.value.TaskStatus;

import java.util.Objects;

public record EmbedExecution(
        TaskId taskId,
        DocumentId docId,
        TaskStatus taskStatus,
        int processedChunks,
        String errorMessage
) {

    public EmbedExecution {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(docId, "docId must not be null");
        Objects.requireNonNull(taskStatus, "taskStatus must not be null");
    }

    public static EmbedExecution create(TaskId taskId, DocumentId docId) {
        return new EmbedExecution(taskId, docId, TaskStatus.PROCESSING, 0, null);
    }

    public EmbedExecution complete(int processedChunks) {
        if (processedChunks < 0) {
            throw new IllegalArgumentException("processedChunks must be >= 0");
        }
        return new EmbedExecution(taskId, docId, TaskStatus.SUCCESS, processedChunks, null);
    }

    public EmbedExecution fail(String errorMessage) {
        return new EmbedExecution(taskId, docId, TaskStatus.FAILED, processedChunks, normalizeError(errorMessage));
    }

    private String normalizeError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown embedding error";
        }
        return raw.trim();
    }
}
