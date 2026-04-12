package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcQaRepository implements QaRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcQaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecallCandidate> vectorRecall(String vectorLiteral, int topK, QaFilters filters) {
        StringBuilder sql = new StringBuilder("""
                select lc.id as chunk_id,
                       sd.law_name,
                       lc.article_no,
                       lc.content,
                       (1 - (lce.embedding <=> ?::vector)) as score,
                       lc.page_start,
                       lc.page_end,
                       sd.version_no
                  from indexing.law_chunk_embedding lce
                  join ingest.law_chunk lc on lc.id = lce.chunk_id
                  join ingest.source_document sd on sd.id = lc.doc_id
                 where sd.status = 'active'
                   and lc.status = 'active'
                """);
        List<Object> args = new ArrayList<>();
        args.add(vectorLiteral);
        appendMetadataFilter(sql, args, filters);
        sql.append(" order by lce.embedding <=> ?::vector asc limit ?");
        args.add(vectorLiteral);
        args.add(topK);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new RecallCandidate(
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        truncate(rs.getString("content")),
                        rs.getDouble("score"),
                        rs.getObject("page_start", Integer.class),
                        rs.getObject("page_end", Integer.class),
                        rs.getString("version_no")
                ),
                args.toArray()
        );
    }

    @Override
    public List<RecallCandidate> keywordRecall(String normalizedQuestion, List<String> keywords, int topK, QaFilters filters, String ftsLanguage) {
        List<String> queryTerms = toQueryTerms(normalizedQuestion, keywords);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        String keywordOrQuery = String.join(" OR ", queryTerms);
        String contentLikeExpr = buildIlikeOrExpression("lc.content", queryTerms.size());
        String lawNameLikeExpr = buildIlikeOrExpression("sd.law_name", queryTerms.size());
        String articleLikeExpr = buildIlikeOrExpression("coalesce(lc.article_no, '')", queryTerms.size());

        StringBuilder sql = new StringBuilder("""
                select lc.id as chunk_id,
                       sd.law_name,
                       lc.article_no,
                       lc.content,
                       greatest(
                           ts_rank_cd(to_tsvector(cast(? as regconfig), coalesce(lc.content, '')),
                                      websearch_to_tsquery(cast(? as regconfig), ?)),
                           case when %s then 0.60 else 0 end,
                           case when %s then 0.80 else 0 end,
                           case when %s then 0.70 else 0 end
                       ) as score,
                       lc.page_start,
                       lc.page_end,
                       sd.version_no
                  from ingest.law_chunk lc
                  join ingest.source_document sd on sd.id = lc.doc_id
                 where sd.status = 'active'
                   and lc.status = 'active'
                   and (
                       to_tsvector(cast(? as regconfig), coalesce(lc.content, '')) @@ websearch_to_tsquery(cast(? as regconfig), ?)
                       or %s
                       or %s
                       or %s
                   )
                """.formatted(contentLikeExpr, lawNameLikeExpr, articleLikeExpr, contentLikeExpr, lawNameLikeExpr, articleLikeExpr));

        List<Object> args = new ArrayList<>();
        args.add(ftsLanguage);
        args.add(ftsLanguage);
        args.add(keywordOrQuery);
        appendLikeArgs(args, queryTerms);
        appendLikeArgs(args, queryTerms);
        appendLikeArgs(args, queryTerms);

        args.add(ftsLanguage);
        args.add(ftsLanguage);
        args.add(keywordOrQuery);
        appendLikeArgs(args, queryTerms);
        appendLikeArgs(args, queryTerms);
        appendLikeArgs(args, queryTerms);

        appendMetadataFilter(sql, args, filters);
        sql.append(" order by score desc, lc.id asc limit ?");
        args.add(topK);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new RecallCandidate(
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        truncate(rs.getString("content")),
                        rs.getDouble("score"),
                        rs.getObject("page_start", Integer.class),
                        rs.getObject("page_end", Integer.class),
                        rs.getString("version_no")
                ),
                args.toArray()
        );
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

    private String buildIlikeOrExpression(String columnExpr, int termCount) {
        return java.util.stream.IntStream.range(0, termCount)
                .mapToObj(i -> columnExpr + " ilike ?")
                .collect(Collectors.joining(" or ", "(", ")"));
    }

    private void appendLikeArgs(List<Object> args, List<String> terms) {
        for (String term : terms) {
            args.add("%" + term + "%");
        }
    }

    @Override
    public Set<Long> filterChunkIdsByMetadata(List<Long> chunkIds, QaFilters filters) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Set.of();
        }
        StringBuilder sql = new StringBuilder("""
                select lc.id
                  from ingest.law_chunk lc
                  join ingest.source_document sd on sd.id = lc.doc_id
                 where sd.status = 'active'
                   and lc.status = 'active'
                   and lc.id in (
                """);
        List<Object> args = new ArrayList<>();
        appendInClause(sql, args, chunkIds);
        sql.append(")");
        appendMetadataFilter(sql, args, filters);

        return new LinkedHashSet<>(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("id"), args.toArray()));
    }

    @Override
    public Set<Long> findChunkIdsByIndustryTags(List<Long> chunkIds, List<String> industryTags) {
        if (chunkIds == null || chunkIds.isEmpty() || industryTags == null || industryTags.isEmpty()) {
            return Set.of();
        }
        StringBuilder sql = new StringBuilder("""
                select distinct ct.chunk_id
                  from ingest.chunk_tag ct
                 where ct.tag_type = 'industry'
                   and ct.chunk_id in (
                """);
        List<Object> args = new ArrayList<>();
        appendInClause(sql, args, chunkIds);
        sql.append(") and ct.tag_value in (");
        appendInClause(sql, args, industryTags);
        sql.append(")");
        return new LinkedHashSet<>(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("chunk_id"), args.toArray()));
    }

    @Override
    public void insertQaRecord(Long id, String question, String normalizedQuestion, String answer, int elapsedMs, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_record
                        (id, question, normalized_question, answer, answer_status, elapsed_ms, created_at, updated_at)
                        values (?, ?, ?, ?, 'success', ?, ?, ?)
                        """,
                id,
                question,
                normalizedQuestion,
                answer,
                elapsedMs,
                now,
                now
        );
    }

    @Override
    public void insertRejectedQaRecord(Long id, String question, String normalizedQuestion, String rejectReason, int elapsedMs, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_record
                        (id, question, normalized_question, answer, answer_status, reject_reason, elapsed_ms, created_at, updated_at)
                        values (?, ?, ?, null, 'reject', ?, ?, ?, ?)
                        """,
                id,
                question,
                normalizedQuestion,
                rejectReason,
                elapsedMs,
                now,
                now
        );
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
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_retrieval_snapshot
                        (id, qa_id, filters_json, query_embedding_model, topk_requested, topn_returned, keyword_query, created_at, updated_at)
                        values (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                qaId,
                filtersJson,
                modelName,
                topK,
                topN,
                keywordQuery,
                now,
                now
        );
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
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_candidate
                        (id, qa_id, chunk_id, source_type, raw_score, rerank_score, final_score, rank_no, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                qaId,
                chunkId,
                sourceType,
                rawScore,
                rerankScore,
                finalScore,
                rankNo,
                now,
                now
        );
    }

    @Override
    public void insertEvidence(Long id, Long qaId, RecallCandidate candidate, int citeNo, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_evidence
                        (id, qa_id, chunk_id, cite_no, quoted_text, used_in_answer, law_name, article_no, page_start, page_end, file_version, created_at, updated_at)
                        values (?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?, ?, ?)
                        """,
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
                now,
                now
        );
    }

    @Override
    public Optional<QaDetail> findQaDetail(Long qaId) {
        return jdbcTemplate.query(
                """
                        select id, question, normalized_question, answer, answer_status, created_at
                          from retrieval.qa_record
                         where id = ?
                        """,
                (rs, rowNum) -> new QaDetail(
                        rs.getLong("id"),
                        rs.getString("question"),
                        rs.getString("normalized_question"),
                        rs.getString("answer"),
                        rs.getString("answer_status"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        findQaEvidences(qaId)
                ),
                qaId
        ).stream().findFirst();
    }

    @Override
    public List<QaEvidence> findQaEvidences(Long qaId) {
        return jdbcTemplate.query(
                """
                        select e.cite_no,
                               e.chunk_id,
                               e.law_name,
                               e.article_no,
                               e.quoted_text,
                               c.source_type,
                               c.final_score,
                               e.page_start,
                               e.page_end,
                               e.file_version
                          from retrieval.qa_evidence e
                          left join retrieval.qa_candidate c on c.qa_id = e.qa_id and c.chunk_id = e.chunk_id
                         where e.qa_id = ?
                         order by e.cite_no
                        """,
                (rs, rowNum) -> new QaEvidence(
                        rs.getObject("cite_no", Integer.class),
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("quoted_text"),
                        rs.getString("source_type"),
                        toDouble(rs.getBigDecimal("final_score")),
                        rs.getObject("page_start", Integer.class),
                        rs.getObject("page_end", Integer.class),
                        rs.getString("file_version")
                ),
                qaId
        );
    }

    private void appendMetadataFilter(StringBuilder sql, List<Object> args, QaFilters filters) {
        if (filters == null) {
            return;
        }
        if (filters.docTypes() != null && !filters.docTypes().isEmpty()) {
            sql.append(" and sd.doc_type in (");
            appendInClause(sql, args, filters.docTypes());
            sql.append(")");
        }
        if (filters.publishOrg() != null && !filters.publishOrg().isBlank()) {
            sql.append(" and sd.publish_org = ?");
            args.add(filters.publishOrg());
        }
        if (filters.effectiveOn() != null) {
            sql.append(" and (sd.effective_date is null or sd.effective_date <= ?)");
            sql.append(" and (sd.expired_date is null or sd.expired_date >= ?)");
            args.add(filters.effectiveOn());
            args.add(filters.effectiveOn());
        }
        if (filters.industryTags() != null && !filters.industryTags().isEmpty()) {
            sql.append(" and exists (select 1 from ingest.chunk_tag ct where ct.chunk_id = lc.id and ct.tag_type = 'industry' and ct.tag_value in (");
            appendInClause(sql, args, filters.industryTags());
            sql.append("))");
        }
    }

    private void appendInClause(StringBuilder sql, List<Object> args, List<?> values) {
        String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
        sql.append(placeholders);
        args.addAll(values);
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
