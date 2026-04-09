package my.inspectorrag.searchandreturn.domain.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface QaRepository {

    List<RecallCandidate> vectorRecall(String vectorLiteral, int topK);

    void insertQaRecord(Long id, String question, String normalizedQuestion, String answer, int elapsedMs, OffsetDateTime now);

    void insertRetrievalSnapshot(Long id, Long qaId, String modelName, int topK, int topN, OffsetDateTime now);

    void insertCandidate(Long id, Long qaId, RecallCandidate candidate, int rankNo, OffsetDateTime now);

    void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now);

    Optional<QaDetail> findQaDetail(Long qaId);

    List<RecallCandidate> findQaEvidences(Long qaId);
}
