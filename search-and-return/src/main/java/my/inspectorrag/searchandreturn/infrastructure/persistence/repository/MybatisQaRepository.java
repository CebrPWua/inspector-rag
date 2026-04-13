package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaCommandMapper;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaQueryMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Primary
@Repository
public class MybatisQaRepository implements QaRepository {

    private final QaQueryMapper queryMapper;
    private final QaCommandMapper commandMapper;

    public MybatisQaRepository(QaQueryMapper queryMapper, QaCommandMapper commandMapper) {
        this.queryMapper = queryMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    public List<RecallCandidate> keywordRecall(String normalizedQuestion, List<String> keywords, int topK, QaFilters filters, String ftsLanguage) {
        List<String> queryTerms = toQueryTerms(normalizedQuestion, keywords);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        List<String> likeTerms = queryTerms.stream().map(term -> "%" + term + "%").toList();
        QaFilters normalized = normalizeFilters(filters);
        String keywordQuery = String.join(" OR ", queryTerms);
        return queryMapper.keywordRecall(
                        ftsLanguage,
                        keywordQuery,
                        likeTerms,
                        topK,
                        normalized.docTypes(),
                        normalized.publishOrg(),
                        normalized.effectiveOn(),
                        normalized.industryTags()
                ).stream()
                .map(row -> new RecallCandidate(
                        row.chunkId(),
                        row.lawName(),
                        row.articleNo(),
                        truncate(row.content()),
                        row.score(),
                        row.pageStart(),
                        row.pageEnd(),
                        row.versionNo()
                ))
                .toList();
    }

    @Override
    public Set<Long> filterChunkIdsByMetadata(List<Long> chunkIds, QaFilters filters) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Set.of();
        }
        QaFilters normalized = normalizeFilters(filters);
        return new LinkedHashSet<>(queryMapper.filterChunkIdsByMetadata(
                chunkIds,
                normalized.docTypes(),
                normalized.publishOrg(),
                normalized.effectiveOn(),
                normalized.industryTags()
        ));
    }

    @Override
    public Set<Long> findChunkIdsByIndustryTags(List<Long> chunkIds, List<String> industryTags) {
        if (chunkIds == null || chunkIds.isEmpty() || industryTags == null || industryTags.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(queryMapper.findChunkIdsByIndustryTags(chunkIds, industryTags));
    }

    @Override
    public void insertQaRecord(Long id, String question, String normalizedQuestion, String answer, int elapsedMs, OffsetDateTime now) {
        commandMapper.insertQaRecord(id, question, normalizedQuestion, answer, elapsedMs, now);
    }

    @Override
    public void insertRejectedQaRecord(Long id, String question, String normalizedQuestion, String rejectReason, int elapsedMs, OffsetDateTime now) {
        commandMapper.insertRejectedQaRecord(id, question, normalizedQuestion, rejectReason, elapsedMs, now);
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
        commandMapper.insertRetrievalSnapshot(id, qaId, modelName, topK, topN, filtersJson, keywordQuery, now);
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
        commandMapper.insertCandidate(id, qaId, chunkId, sourceType, rawScore, rerankScore, finalScore, rankNo, now);
    }

    @Override
    public void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now) {
        commandMapper.insertEvidence(
                id,
                qaId,
                candidate.chunkId(),
                citeNo,
                candidate.content(),
                candidate.lawName(),
                candidate.articleNo(),
                candidate.pageStart(),
                candidate.pageEnd(),
                candidate.versionNo(),
                now
        );
    }

    @Override
    public Optional<QaDetail> findQaDetail(Long qaId) {
        var row = queryMapper.findQaDetail(qaId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new QaDetail(
                row.qaId(),
                row.question(),
                row.normalizedQuestion(),
                row.answer(),
                row.answerStatus(),
                row.createdAt(),
                findQaEvidences(qaId)
        ));
    }

    @Override
    public List<QaEvidence> findQaEvidences(Long qaId) {
        return queryMapper.findQaEvidences(qaId).stream()
                .map(row -> new QaEvidence(
                        row.citeNo(),
                        row.chunkId(),
                        row.lawName(),
                        row.articleNo(),
                        row.quotedText(),
                        row.sourceType(),
                        toDouble(row.finalScore()),
                        row.pageStart(),
                        row.pageEnd(),
                        row.fileVersion()
                ))
                .toList();
    }

    private QaFilters normalizeFilters(QaFilters filters) {
        return filters == null ? QaFilters.empty() : filters;
    }

    private List<String> toQueryTerms(String normalizedQuestion, List<String> keywords) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                addTerms(terms, keyword);
            }
        }
        addTerms(terms, normalizedQuestion);
        return terms.stream().limit(10).toList();
    }

    private void addTerms(Set<String> terms, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 2) {
            terms.add(trimmed);
        }
        String[] split = trimmed.split("[\\s,，。;；:：()（）【】\\[\\]、]+");
        for (String token : split) {
            if (token == null) {
                continue;
            }
            String cleaned = token.trim();
            if (cleaned.length() >= 2) {
                terms.add(cleaned);
            }
        }
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 500 ? content : content.substring(0, 500);
    }
}
