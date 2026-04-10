package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface DocumentCommandMapper {

    @Select("""
            select id
              from ingest.source_document
             where file_hash = #{fileHash}
             limit 1
            """)
    Long findDocIdByFileHash(@Param("fileHash") String fileHash);

    @Insert("""
            insert into ingest.source_document
            (id, law_name, law_code, doc_type, source_file_name, file_hash, version_no, status, parse_status, created_at, updated_at)
            values (#{id}, #{lawName}, #{lawCode}, #{docType}, #{sourceFileName}, #{fileHash}, #{versionNo}, #{status}, 'pending', #{now}, #{now})
            """)
    void insertSourceDocument(
            @Param("id") Long id,
            @Param("lawName") String lawName,
            @Param("lawCode") String lawCode,
            @Param("docType") String docType,
            @Param("sourceFileName") String sourceFileName,
            @Param("fileHash") String fileHash,
            @Param("versionNo") String versionNo,
            @Param("status") String status,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into ingest.document_file
            (id, doc_id, storage_path, mime_type, file_size_bytes, sha256, upload_batch_no, is_primary, created_at, updated_at)
            values (#{id}, #{docId}, #{storagePath}, #{mimeType}, #{fileSize}, #{sha256}, #{uploadBatchNo}, true, #{now}, #{now})
            """)
    void insertDocumentFile(
            @Param("id") Long id,
            @Param("docId") Long docId,
            @Param("storagePath") String storagePath,
            @Param("mimeType") String mimeType,
            @Param("fileSize") long fileSize,
            @Param("sha256") String sha256,
            @Param("uploadBatchNo") String uploadBatchNo,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into ops.import_task
            (id, doc_id, task_type, task_status, retry_count, max_retry, created_at, updated_at)
            values (#{id}, #{docId}, #{taskType}, 'pending', 0, 3, #{now}, #{now})
            """)
    void insertImportTask(
            @Param("id") Long id,
            @Param("docId") Long docId,
            @Param("taskType") String taskType,
            @Param("now") OffsetDateTime now
    );
}
