package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Primary
@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "mybatis")
public class MybatisQaRepository implements QaRepository {

    private final JdbcQaRepository delegate;

    public MybatisQaRepository(JdbcTemplate jdbcTemplate) {
        this.delegate = new JdbcQaRepository(jdbcTemplate);
    }

    @Override
    public List<RecallCandidate> vectorRecall(String vectorLiteral, int topK, QaFilters filters) {
        return delegate.vectorRecall(vectorLiteral, topK, filters);
    }

    @Override
    public List<RecallCandidate> keywordRecall(String normalizedQuestion, List<String> keywords, int topK, QaFilters filters, String ftsLanguage) {
        return delegate.keywordRecall(normalizedQuestion, keywords, topK, filters, ftsLanguage);
    }

    @Override
    public Set<Long> filterChunkIdsByMetadata(List<Long> chunkIds, QaFilters filters) {
        return delegate.filterChunkIdsByMetadata(chunkIds, filters);
    }

    @Override
    public Set<Long> findChunkIdsByIndustryTags(List<Long> chunkIds, List<String> industryTags) {
        return delegate.findChunkIdsByIndustryTags(chunkIds, industryTags);
    }

    @Override
    public void insertQaRecord(Long id, String question, String normalizedQuestion, String answer, int elapsedMs, OffsetDateTime now) {
        delegate.insertQaRecord(id, question, normalizedQuestion, answer, elapsedMs, now);
    }

    @Override
    public void insertRejectedQaRecord(Long id, String question, String normalizedQuestion, String rejectReason, int elapsedMs, OffsetDateTime now) {
        delegate.insertRejectedQaRecord(id, question, normalizedQuestion, rejectReason, elapsedMs, now);
    }

    @Override
    public void insertRetrievalSnapshot(
            Long id,
            Long qaId,
            String modelName,
            int topK,
            int topN,
            String filtersJson,
            String keywordQuery,
            OffsetDateTime now
    ) {
        delegate.insertRetrievalSnapshot(id, qaId, modelName, topK, topN, filtersJson, keywordQuery, now);
    }

    @Override
    public void insertCandidate(
            Long id,
            Long qaId,
            Long chunkId,
            String sourceType,
            Double rawScore,
            Double rerankScore,
            Double finalScore,
            int rankNo,
            OffsetDateTime now
    ) {
        delegate.insertCandidate(id, qaId, chunkId, sourceType, rawScore, rerankScore, finalScore, rankNo, now);
    }

    @Override
    public void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now) {
        delegate.insertEvidence(id, qaId, candidate, citeNo, now);
    }

    @Override
    public Optional<QaDetail> findQaDetail(Long qaId) {
        return delegate.findQaDetail(qaId);
    }

    @Override
    public List<QaEvidence> findQaEvidences(Long qaId) {
        return delegate.findQaEvidences(qaId);
    }
}
