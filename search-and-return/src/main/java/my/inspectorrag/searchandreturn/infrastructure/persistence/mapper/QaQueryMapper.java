package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QaQueryMapper {

    @Select("""
            select lc.id as chunk_id,
                   sd.law_name,
                   lc.article_no,
                   lc.content,
                   (1 - (lce.embedding <=> #{vectorLiteral}::vector)) as score,
                   lc.page_start,
                   lc.page_end,
                   sd.version_no
              from indexing.law_chunk_embedding lce
              join ingest.law_chunk lc on lc.id = lce.chunk_id
              join ingest.source_document sd on sd.id = lc.doc_id
             where sd.status = 'active'
               and lc.status = 'active'
             order by lce.embedding <=> #{vectorLiteral}::vector asc
             limit #{topK}
            """)
    List<RecallCandidateRow> vectorRecall(@Param("vectorLiteral") String vectorLiteral, @Param("topK") int topK);

    @Select("""
            select id as qa_id, question, normalized_question, answer, answer_status, created_at
              from retrieval.qa_record
             where id = #{qaId}
            """)
    QaDetailRow findQaDetail(@Param("qaId") Long qaId);

    @Select("""
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
             where e.qa_id = #{qaId}
             order by e.cite_no
            """)
    List<QaEvidenceRow> findQaEvidences(@Param("qaId") Long qaId);
}
