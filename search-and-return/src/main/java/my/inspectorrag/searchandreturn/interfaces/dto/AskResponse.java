package my.inspectorrag.searchandreturn.interfaces.dto;

import java.util.List;

public record AskResponse(Long qaId, String normalizedQuestion, String answer, List<EvidenceDto> evidences) {
}
