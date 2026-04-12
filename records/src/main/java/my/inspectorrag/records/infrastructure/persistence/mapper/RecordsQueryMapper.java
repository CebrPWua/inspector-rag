package my.inspectorrag.records.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface RecordsQueryMapper {

    @Select("""
            select id as qa_id, question, answer_status, elapsed_ms, created_at
              from retrieval.qa_record
             order by created_at desc
             limit #{limit}
            """)
    List<QaRecordRow> listQa(@Param("limit") int limit);

    @Select("""
            select id as qa_id, question, normalized_question, answer
              from retrieval.qa_record
             where id = #{qaId}
            """)
    QaReplayRow findReplayHeader(@Param("qaId") Long qaId);

    @Select("""
            select chunk_id, source_type, raw_score, final_score, rank_no
              from retrieval.qa_candidate
             where qa_id = #{qaId}
             order by rank_no
            """)
    List<QaCandidateRow> listCandidates(@Param("qaId") Long qaId);

    @Select("""
            select cite_no, chunk_id, law_name, article_no, quoted_text
              from retrieval.qa_evidence
             where qa_id = #{qaId}
             order by cite_no
            """)
    List<QaEvidenceRow> listEvidences(@Param("qaId") Long qaId);

    @Select("""
            with filtered as (
                select id, answer_status, elapsed_ms
                  from retrieval.qa_record
                 where created_at >= #{from}
                   and created_at < #{to}
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
            """)
    QaQualityMetricsRow qualityMetrics(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Select("""
            select case
                       when position(':' in reject_reason) > 0
                           then btrim(substring(reject_reason from 1 for position(':' in reject_reason) - 1))
                       when reject_reason is null or btrim(reject_reason) = ''
                           then 'UNKNOWN'
                       else btrim(reject_reason)
                   end as reason_code,
                   count(*)::bigint as cnt
              from retrieval.qa_record
             where created_at >= #{from}
               and created_at < #{to}
               and answer_status = 'reject'
             group by reason_code
             order by cnt desc, reason_code asc
             limit 10
            """)
    List<RejectReasonStatRow> topRejectReasons(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
