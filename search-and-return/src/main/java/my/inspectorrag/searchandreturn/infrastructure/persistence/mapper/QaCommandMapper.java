package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

@Mapper
public interface QaCommandMapper {

    @Insert("""
            insert into retrieval.qa_record
            (id, question, normalized_question, answer, answer_status, elapsed_ms, created_at, updated_at)
            values (#{id}, #{question}, #{normalizedQuestion}, #{answer}, 'success', #{elapsedMs}, #{now}, #{now})
            """)
    void insertQaRecord(
            @Param("id") Long id,
            @Param("question") String question,
            @Param("normalizedQuestion") String normalizedQuestion,
            @Param("answer") String answer,
            @Param("elapsedMs") int elapsedMs,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into retrieval.qa_retrieval_snapshot
            (id, qa_id, filters_json, query_embedding_model, topk_requested, topn_returned, keyword_query, created_at, updated_at)
            values (#{id}, #{qaId}, #{filtersJson}::jsonb, #{modelName}, #{topK}, #{topN}, #{keywordQuery}, #{now}, #{now})
            """)
    void insertRetrievalSnapshot(
            @Param("id") Long id,
            @Param("qaId") Long qaId,
            @Param("modelName") String modelName,
            @Param("topK") int topK,
            @Param("topN") int topN,
            @Param("filtersJson") String filtersJson,
            @Param("keywordQuery") String keywordQuery,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into retrieval.qa_candidate
            (id, qa_id, chunk_id, source_type, raw_score, rerank_score, final_score, rank_no, created_at, updated_at)
            values (#{id}, #{qaId}, #{chunkId}, #{sourceType}, #{rawScore}, #{rerankScore}, #{finalScore}, #{rankNo}, #{now}, #{now})
            """)
    void insertCandidate(
            @Param("id") Long id,
            @Param("qaId") Long qaId,
            @Param("chunkId") Long chunkId,
            @Param("sourceType") String sourceType,
            @Param("rawScore") Double rawScore,
            @Param("rerankScore") Double rerankScore,
            @Param("finalScore") Double finalScore,
            @Param("rankNo") int rankNo,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into retrieval.qa_record
            (id, question, normalized_question, answer, answer_status, reject_reason, elapsed_ms, created_at, updated_at)
            values (#{id}, #{question}, #{normalizedQuestion}, null, 'reject', #{rejectReason}, #{elapsedMs}, #{now}, #{now})
            """)
    void insertRejectedQaRecord(
            @Param("id") Long id,
            @Param("question") String question,
            @Param("normalizedQuestion") String normalizedQuestion,
            @Param("rejectReason") String rejectReason,
            @Param("elapsedMs") int elapsedMs,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into retrieval.qa_evidence
            (id, qa_id, chunk_id, cite_no, quoted_text, used_in_answer, law_name, article_no, page_start, page_end, file_version, created_at, updated_at)
            values (#{id}, #{qaId}, #{chunkId}, #{citeNo}, #{quotedText}, true, #{lawName}, #{articleNo}, #{pageStart}, #{pageEnd}, #{fileVersion}, #{now}, #{now})
            """)
    void insertEvidence(
            @Param("id") Long id,
            @Param("qaId") Long qaId,
            @Param("chunkId") Long chunkId,
            @Param("citeNo") int citeNo,
            @Param("quotedText") String quotedText,
            @Param("lawName") String lawName,
            @Param("articleNo") String articleNo,
            @Param("pageStart") Integer pageStart,
            @Param("pageEnd") Integer pageEnd,
            @Param("fileVersion") String fileVersion,
            @Param("now") OffsetDateTime now
    );
}
