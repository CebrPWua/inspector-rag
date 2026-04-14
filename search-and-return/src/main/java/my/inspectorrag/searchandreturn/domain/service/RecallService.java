package my.inspectorrag.searchandreturn.domain.service;

import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;

import java.util.List;

public interface RecallService {

    List<RecallCandidate> recall(String normalizedQuestion, int topK, QaFilters filters);

    default List<RecallCandidate> recall(String normalizedQuestion, int topK, QaFilters filters, String routeKey) {
        return recall(normalizedQuestion, topK, filters);
    }

    default String resolveProfileKey(String routeKey) {
        return null;
    }
}
