package my.inspectorrag.searchandreturn.interfaces.dto;

import java.util.List;

public record AskResponse(String qaId, String normalizedQuestion, String answer, List<EvidenceDto> evidences) {
}
