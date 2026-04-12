package my.inspectorrag.embedding.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

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

    @Update("update indexing.embedding_model set is_active = true where id = #{id}")
    void activateEmbeddingModel(@Param("id") Long id);

    @Insert("""
            insert into indexing.embedding_model(id, model_name, dimension, version, provider, is_active, created_at, updated_at)
            values (#{id}, #{modelName}, #{dimension}, #{version}, #{provider}, true, #{now}, #{now})
            on conflict (model_name, version)
            do update set is_active = excluded.is_active
            """)
    void insertOrActivateEmbeddingModel(
            @Param("id") Long id,
            @Param("modelName") String modelName,
            @Param("version") String version,
            @Param("dimension") int dimension,
            @Param("provider") String provider,
            @Param("now") OffsetDateTime now
    );

    @Update("update ingest.law_chunk set embedding_status = #{status} where id = #{chunkId}")
    void markChunkStatus(@Param("chunkId") Long chunkId, @Param("status") String status);

    @Insert("""
            insert into indexing.law_chunk_embedding
            (id, chunk_id, model_id, embedding_version, embedding, created_at, updated_at)
            values (#{id}, #{chunkId}, #{modelId}, #{embeddingVersion}, #{vectorLiteral}::vector, #{now}, #{now})
            on conflict (chunk_id, model_id, embedding_version)
            do update set embedding = excluded.embedding,
                          updated_at = excluded.updated_at
            """)
    void upsertChunkEmbedding(
            @Param("id") Long id,
            @Param("chunkId") Long chunkId,
            @Param("modelId") Long modelId,
            @Param("embeddingVersion") String embeddingVersion,
            @Param("vectorLiteral") String vectorLiteral,
            @Param("now") OffsetDateTime now
    );
}
