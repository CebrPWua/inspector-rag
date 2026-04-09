package my.inspectorrag.docanalyzing.interfaces.dto;

import jakarta.validation.constraints.NotNull;

public record ParseTaskRequest(
        @NotNull Long taskId,
        @NotNull Long docId
) {
}
