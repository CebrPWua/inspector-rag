package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentQueryMapper {

    @Select("""
            select sd.id as doc_id,
                   sd.law_name,
                   sd.law_code,
                   sd.version_no,
                   sd.status,
                   sd.parse_status,
                   sd.source_file_name,
                   sd.file_hash,
                   df.storage_path,
                   sd.created_at
              from ingest.source_document sd
              left join ingest.document_file df on df.doc_id = sd.id and df.is_primary = true
             where sd.id = #{docId}
            """)
    FileDetailRow findFileDetail(@Param("docId") Long docId);
}
