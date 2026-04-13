package my.inspectorrag.embedding.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmbeddingCommandMapper {

    @Update("""
            update ops.import_task
               set task_status = #{status},
                   error_msg = #{errorMsg},
                   finished_at = case when #{status} in ('success', 'failed') then now() else finished_at end
             where id = #{taskId}
            """)
    void markTaskStatus(
            @Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("errorMsg") String errorMsg
    );

    @Update("update ingest.law_chunk set embedding_status = #{status} where id = #{chunkId}")
    void markChunkStatus(@Param("chunkId") Long chunkId, @Param("status") String status);
}
