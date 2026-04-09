package my.inspectorrag.searchandreturn.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank String question) {
}
