package my.inspectorrag.searchandreturn.domain.service;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;

import java.util.List;

public interface AnswerGenerator {

    String generate(String normalizedQuestion, List<RecallCandidate> candidates);
}

