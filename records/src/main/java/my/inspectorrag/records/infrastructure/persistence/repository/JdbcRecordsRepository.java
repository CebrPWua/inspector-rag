package my.inspectorrag.records.infrastructure.persistence.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaReplayCandidate;
import my.inspectorrag.records.domain.model.QaReplayEvidence;
import my.inspectorrag.records.domain.model.QaQualityReport;
import my.inspectorrag.records.domain.model.RejectReasonStat;
import my.inspectorrag.records.domain.repository.RecordsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "jdbc", matchIfMissing = true)
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

    @Override
    public QaQualityReport qualityReport(OffsetDateTime from, OffsetDateTime to) {
        var metrics = jdbcTemplate.queryForObject(
                """
                        with filtered as (
                            select id, answer_status, elapsed_ms
                              from retrieval.qa_record
                             where created_at >= ?
                               and created_at < ?
                        ),
                        evidence_counts as (
                            select qa_id, count(*)::double precision as evidence_count
                              from retrieval.qa_evidence
                             where qa_id in (select id from filtered)
                             group by qa_id
                        ),
                        top1_scores as (
                            select qa_id, final_score::double precision as top1_final_score
                              from retrieval.qa_candidate
                             where rank_no = 1
                               and qa_id in (select id from filtered)
                        )
                        select count(*)::bigint as total,
                               count(*) filter (where answer_status = 'success')::bigint as success,
                               count(*) filter (where answer_status = 'reject')::bigint as reject,
                               count(*) filter (where answer_status = 'failed')::bigint as failed,
                               avg(elapsed_ms)::double precision as avg_elapsed_ms,
                               percentile_cont(0.95) within group (order by elapsed_ms)::double precision as p95_elapsed_ms,
                               (select avg(evidence_count) from evidence_counts) as avg_evidence_count,
                               (select avg(top1_final_score) from top1_scores) as avg_top1_final_score
                          from filtered
                        """,
                (rs, rowNum) -> new QaQualityReport(
                        rs.getLong("total"),
                        rs.getLong("success"),
                        rs.getLong("reject"),
                        rs.getLong("failed"),
                        rs.getObject("avg_elapsed_ms", Double.class),
                        rs.getObject("p95_elapsed_ms", Double.class),
                        rs.getObject("avg_evidence_count", Double.class),
                        rs.getObject("avg_top1_final_score", Double.class),
                        listTopRejectReasons(from, to)
                ),
                from,
                to
        );
        if (metrics == null) {
            return new QaQualityReport(0, 0, 0, 0, null, null, null, null, List.of());
        }
        return metrics;
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

    private List<RejectReasonStat> listTopRejectReasons(OffsetDateTime from, OffsetDateTime to) {
        return jdbcTemplate.query(
                """
                        select case
                                   when position(':' in reject_reason) > 0
                                       then btrim(substring(reject_reason from 1 for position(':' in reject_reason) - 1))
                                   when reject_reason is null or btrim(reject_reason) = ''
                                       then 'UNKNOWN'
                                   else btrim(reject_reason)
                               end as reason_code,
                               count(*)::bigint as cnt
                          from retrieval.qa_record
                         where created_at >= ?
                           and created_at < ?
                           and answer_status = 'reject'
                         group by reason_code
                         order by cnt desc, reason_code asc
                         limit 10
                        """,
                (rs, rowNum) -> new RejectReasonStat(
                        rs.getString("reason_code"),
                        rs.getLong("cnt")
                ),
                from,
                to
        );
    }
}
