package my.inspectorrag.searchandreturn.domain.service;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;

import java.util.List;

public interface RecallService {

    List<RecallCandidate> recall(String normalizedQuestion, int topK);
}
