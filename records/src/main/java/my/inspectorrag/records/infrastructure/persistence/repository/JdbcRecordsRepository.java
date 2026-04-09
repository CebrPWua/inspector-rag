package my.inspectorrag.records.infrastructure.persistence.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRecordsRepository implements RecordsRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRecordsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<QaRecordItem> listQa(int limit) {
        return jdbcTemplate.query(
                """
                        select id, question, answer_status, elapsed_ms, created_at
                          from retrieval.qa_record
                         order by created_at desc
                         limit ?
                        """,
                (rs, rowNum) -> new QaRecordItem(
                        rs.getLong("id"),
                        rs.getString("question"),
                        rs.getString("answer_status"),
                        rs.getObject("elapsed_ms", Integer.class),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)
                ),
                limit
        );
    }

    @Override
    public Optional<QaReplay> replay(Long qaId) {
        return jdbcTemplate.query(
                """
                        select id, question, normalized_question, answer
                          from retrieval.qa_record
                         where id = ?
                        """,
                (rs, rowNum) -> new QaReplay(
                        rs.getLong("id"),
                        rs.getString("question"),
                        rs.getString("normalized_question"),
                        rs.getString("answer"),
                        findCandidates(qaId),
                        findEvidences(qaId)
                ),
                qaId
        ).stream().findFirst();
    }

    private List<QaReplayCandidate> findCandidates(Long qaId) {
        return jdbcTemplate.query(
                """
                        select chunk_id, source_type, raw_score, final_score, rank_no
                          from retrieval.qa_candidate
                         where qa_id = ?
                         order by rank_no
                        """,
                (rs, rowNum) -> new QaReplayCandidate(
                        rs.getLong("chunk_id"),
                        rs.getString("source_type"),
                        toDouble(rs.getBigDecimal("raw_score")),
                        toDouble(rs.getBigDecimal("final_score")),
                        rs.getObject("rank_no", Integer.class)
                ),
                qaId
        );
    }

    private List<QaReplayEvidence> findEvidences(Long qaId) {
        return jdbcTemplate.query(
                """
                        select cite_no, chunk_id, law_name, article_no, quoted_text
                          from retrieval.qa_evidence
                         where qa_id = ?
                         order by cite_no
                        """,
                (rs, rowNum) -> new QaReplayEvidence(
                        rs.getInt("cite_no"),
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("article_no"),
                        rs.getString("quoted_text")
                ),
                qaId
        );
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
