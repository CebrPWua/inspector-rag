package my.inspectorrag.embedding.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmbeddingQueryMapper {

    @Select("""
            select id
              from indexing.embedding_model
             where model_name = #{modelName}
               and version = #{version}
             limit 1
            """)
    Long findEmbeddingModelId(@Param("modelName") String modelName, @Param("version") String version);

    @Select("""
            select id
              from indexing.embedding_model
             where model_name = #{modelName}
               and version = #{version}
            """)
    Long requireEmbeddingModelId(@Param("modelName") String modelName, @Param("version") String version);

    @Select("""
            select lc.id as chunk_id,
                   sd.law_name,
                   lc.chapter_title,
                   lc.section_title,
                   lc.article_no,
                   lc.content,
                   lc.page_start,
                   lc.page_end,
                   sd.version_no,
                   lc.status
              from ingest.law_chunk lc
              join ingest.source_document sd on sd.id = lc.doc_id
             where lc.doc_id = #{docId}
               and lc.embedding_status = 'pending'
             order by lc.id
             limit #{limit}
            """)
    List<PendingChunkRow> findPendingChunks(@Param("docId") Long docId, @Param("limit") int limit);
}
