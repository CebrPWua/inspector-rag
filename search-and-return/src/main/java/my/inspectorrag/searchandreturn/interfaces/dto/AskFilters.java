package my.inspectorrag.searchandreturn.interfaces.dto;

import java.time.LocalDate;
import java.util.List;

public record AskFilters(
        List<String> industryTags,
        List<String> docTypes,
        String publishOrg,
        LocalDate effectiveOn
) {
}
