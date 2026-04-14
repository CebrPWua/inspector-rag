package my.inspectorrag.searchandreturn.domain.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.ConversationMessage;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QaRepository {

    List<RecallCandidate> keywordRecall(String normalizedQuestion, List<String> keywords, int topK, QaFilters filters, String ftsLanguage);

    Set<Long> filterChunkIdsByMetadata(List<Long> chunkIds, QaFilters filters);

    Set<Long> findChunkIdsByIndustryTags(List<Long> chunkIds, List<String> industryTags);

    boolean existsConversation(Long conversationId);

    void insertConversation(Long id, OffsetDateTime now);

    int nextTurnNo(Long conversationId);

    List<ConversationContextTurn> findConversationContext(Long conversationId, int limit);

    void insertQaRecord(
            Long id,
            Long conversationId,
            int turnNo,
            String question,
            String normalizedQuestion,
            String rewrittenQuestion,
            String answer,
            int elapsedMs,
            OffsetDateTime now
    );

    void insertRejectedQaRecord(
            Long id,
            Long conversationId,
            int turnNo,
            String question,
            String normalizedQuestion,
            String rewrittenQuestion,
            String answer,
            String rejectReason,
            int elapsedMs,
            OffsetDateTime now
    );

    void insertRetrievalSnapshot(
            Long id,
            Long qaId,
            String modelName,
            String profileKey,
            int topK,
            int topN,
            String filtersJson,
            String keywordQuery,
            String effectiveQuery,
            String rewriteQueriesJson,
            OffsetDateTime now
    );

    void insertCandidate(
            Long id,
            Long qaId,
            Long chunkId,
            String sourceType,
            Double rawScore,
            Double rerankScore,
            Double finalScore,
            int rankNo,
            OffsetDateTime now
    );

    void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now);

    Optional<QaDetail> findQaDetail(Long qaId);

    List<QaEvidence> findQaEvidences(Long qaId);

    List<ConversationMessage> findConversationMessages(Long conversationId);
}
