package my.inspectorrag.docanalyzing.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
public interface ParseCommandMapper {

    @Update("update ingest.source_document set parse_status = #{parseStatus} where id = #{docId}")
    void updateParseStatus(@Param("docId") Long docId, @Param("parseStatus") String parseStatus);

    @Update("""
            update ops.import_task
               set task_status = #{status},
                   error_msg = #{errorMessage},
                   finished_at = case when #{status} in ('success', 'failed') then now() else finished_at end
             where id = #{taskId}
            """)
    void markTaskStatus(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    @Delete("delete from ingest.chunk_tag where chunk_id in (select id from ingest.law_chunk where doc_id = #{docId})")
    void deleteChunkTagsByDocId(@Param("docId") Long docId);

    @Delete("delete from ingest.law_chunk where doc_id = #{docId}")
    void deleteChunksByDocId(@Param("docId") Long docId);

    @Insert("""
            insert into ingest.law_chunk
            (id, doc_id, chapter_title, section_title, article_no, item_no, content, chunk_seq, content_hash, embedding_status, status, created_at, updated_at)
            values (#{id}, #{docId}, #{chapterTitle}, #{sectionTitle}, #{articleNo}, #{itemNo}, #{content}, #{chunkSeq}, #{contentHash}, 'pending', 'active', #{now}, #{now})
            """)
    void insertChunk(
            @Param("id") Long id,
            @Param("docId") Long docId,
            @Param("chapterTitle") String chapterTitle,
            @Param("sectionTitle") String sectionTitle,
            @Param("articleNo") String articleNo,
            @Param("itemNo") String itemNo,
            @Param("content") String content,
            @Param("chunkSeq") int chunkSeq,
            @Param("contentHash") String contentHash,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into ingest.chunk_tag(id, chunk_id, tag_type, tag_value, created_at, updated_at)
            values (#{id}, #{chunkId}, #{tagType}, #{tagValue}, #{now}, #{now})
            """)
    void insertChunkTag(
            @Param("id") Long id,
            @Param("chunkId") Long chunkId,
            @Param("tagType") String tagType,
            @Param("tagValue") String tagValue,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into ops.import_task(id, doc_id, task_type, task_status, retry_count, max_retry, created_at, updated_at)
            values (#{id}, #{docId}, #{taskType}, 'pending', 0, 3, #{now}, #{now})
            """)
    void insertImportTask(
            @Param("id") Long id,
            @Param("docId") Long docId,
            @Param("taskType") String taskType,
            @Param("now") OffsetDateTime now
    );
}
