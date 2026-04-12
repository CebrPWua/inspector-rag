package my.inspectorrag.taskscheduling.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDeadLetterStatusRequest(
        @NotBlank String status
) {
}
