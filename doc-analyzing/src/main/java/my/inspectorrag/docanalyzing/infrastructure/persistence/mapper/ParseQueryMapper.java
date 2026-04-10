package my.inspectorrag.docanalyzing.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ParseQueryMapper {

    @Select("""
            select storage_path
              from ingest.document_file
             where doc_id = #{docId}
               and is_primary = true
             limit 1
            """)
    String findPrimaryStoragePath(@Param("docId") Long docId);
}
