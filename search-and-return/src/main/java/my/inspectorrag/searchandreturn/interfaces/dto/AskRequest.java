package my.inspectorrag.searchandreturn.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank String question,
        @Valid AskFilters filters
) {
}
