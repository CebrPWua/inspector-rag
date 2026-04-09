package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcQaRepository implements QaRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcQaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecallCandidate> vectorRecall(String vectorLiteral, int topK) {
        return jdbcTemplate.query(
                """
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
                         order by lce.embedding <=> ?::vector asc
                         limit ?
                        """,
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
                vectorLiteral,
                vectorLiteral,
                topK
        );
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
    public void insertRetrievalSnapshot(Long id, Long qaId, String modelName, int topK, int topN, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_retrieval_snapshot
                        (id, qa_id, filters_json, query_embedding_model, topk_requested, topn_returned, created_at, updated_at)
                        values (?, ?, '{}'::jsonb, ?, ?, ?, ?, ?)
                        """,
                id,
                qaId,
                modelName,
                topK,
                topN,
                now,
                now
        );
    }

    @Override
    public void insertCandidate(Long id, Long qaId, RecallCandidate candidate, int rankNo, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into retrieval.qa_candidate
                        (id, qa_id, chunk_id, source_type, raw_score, final_score, rank_no, created_at, updated_at)
                        values (?, ?, ?, 'vector', ?, ?, ?, ?, ?)
                        """,
                id,
                qaId,
                candidate.chunkId(),
                candidate.score(),
                candidate.score(),
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
    public List<RecallCandidate> findQaEvidences(Long qaId) {
        return jdbcTemplate.query(
                """
                        select e.chunk_id,
                               e.law_name,
                               e.article_no,
                               e.quoted_text,
                               c.final_score,
                               e.page_start,
                               e.page_end,
                               e.file_version
                          from retrieval.qa_evidence e
                          left join retrieval.qa_candidate c on c.qa_id = e.qa_id and c.chunk_id = e.chunk_id
                         where e.qa_id = ?
                         order by e.cite_no
                        """,
                (rs, rowNum) -> new RecallCandidate(
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("quoted_text"),
                        toDouble(rs.getBigDecimal("final_score")),
                        rs.getObject("page_start", Integer.class),
                        rs.getObject("page_end", Integer.class),
                        rs.getString("file_version")
                ),
                qaId
        );
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
