package my.inspectorrag.taskscheduling.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignDeadLetterRequest(
        @NotBlank String assignedTo
) {
}
