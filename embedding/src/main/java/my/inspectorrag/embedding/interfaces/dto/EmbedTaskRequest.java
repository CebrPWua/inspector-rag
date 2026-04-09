package my.inspectorrag.embedding.interfaces.dto;

import jakarta.validation.constraints.NotNull;

public record EmbedTaskRequest(
        @NotNull Long taskId,
        @NotNull Long docId
) {
}
