package my.inspectorrag.records.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
