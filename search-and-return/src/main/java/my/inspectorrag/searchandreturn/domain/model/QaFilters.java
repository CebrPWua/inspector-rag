package my.inspectorrag.searchandreturn.domain.model;

import java.time.LocalDate;
import java.util.List;

public record QaFilters(
        List<String> industryTags,
        List<String> docTypes,
        String publishOrg,
        LocalDate effectiveOn
) {
    public static QaFilters empty() {
        return new QaFilters(List.of(), List.of(), null, null);
    }
}
