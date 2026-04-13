package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentQueryMapper {

    @Select("""
            select sd.id as doc_id,
                   sd.law_name,
                   sd.law_code,
                   sd.doc_type,
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

    @Select("""
            select sd.id as doc_id,
                   sd.law_name,
                   sd.law_code,
                   sd.doc_type,
                   sd.version_no,
                   sd.status,
                   sd.parse_status,
                   sd.created_at
              from ingest.source_document sd
             order by sd.created_at desc
             limit #{limit}
            """)
    List<FileListItemRow> listFiles(@Param("limit") int limit);
}
