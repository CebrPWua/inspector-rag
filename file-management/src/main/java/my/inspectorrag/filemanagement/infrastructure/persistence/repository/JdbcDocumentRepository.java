package my.inspectorrag.filemanagement.infrastructure.persistence.repository;

import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findDocIdByFileHash(String fileHash) {
        return jdbcTemplate.query(
                "select id from ingest.source_document where file_hash = ? limit 1",
                (rs, rowNum) -> rs.getLong("id"),
                fileHash
        ).stream().findFirst();
    }

    @Override
    public void insertSourceDocument(
            Long id,
            String lawName,
            String lawCode,
            String docType,
            String sourceFileName,
            String fileHash,
            String versionNo,
            String status,
            OffsetDateTime now
    ) {
        jdbcTemplate.update(
                """
                        insert into ingest.source_document
                        (id, law_name, law_code, doc_type, source_file_name, file_hash, version_no, status, parse_status, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
                        """,
                id, lawName, lawCode, docType, sourceFileName, fileHash, versionNo, status, now, now
        );
    }

    @Override
    public void insertDocumentFile(
            Long id,
            Long docId,
            String storagePath,
            String mimeType,
            long fileSize,
            String sha256,
            String uploadBatchNo,
            OffsetDateTime now
    ) {
        jdbcTemplate.update(
                """
                        insert into ingest.document_file
                        (id, doc_id, storage_path, mime_type, file_size_bytes, sha256, upload_batch_no, is_primary, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                        """,
                id, docId, storagePath, mimeType, fileSize, sha256, uploadBatchNo, now, now
        );
    }

    @Override
    public Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ops.import_task
                        (id, doc_id, task_type, task_status, retry_count, max_retry, created_at, updated_at)
                        values (?, ?, ?, 'pending', 0, 3, ?, ?)
                        """,
                id, docId, taskType, now, now
        );
        return id;
    }

    @Override
    public Optional<FileDetail> findFileDetail(Long docId) {
        return jdbcTemplate.query(
                """
                        select sd.id,
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
                        where sd.id = ?
                        """,
                this::mapFileDetail,
                docId
        ).stream().findFirst();
    }

    private FileDetail mapFileDetail(ResultSet rs, int rowNum) throws SQLException {
        return new FileDetail(
                rs.getLong("id"),
                rs.getString("law_name"),
                rs.getString("law_code"),
                rs.getString("version_no"),
                rs.getString("status"),
                rs.getString("parse_status"),
                rs.getString("source_file_name"),
                rs.getString("file_hash"),
                rs.getString("storage_path"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
