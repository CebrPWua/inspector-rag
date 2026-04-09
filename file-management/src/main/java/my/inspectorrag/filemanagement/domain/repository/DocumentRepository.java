package my.inspectorrag.filemanagement.domain.repository;

import my.inspectorrag.filemanagement.domain.model.FileDetail;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface DocumentRepository {

    Optional<Long> findDocIdByFileHash(String fileHash);

    void insertSourceDocument(
            Long id,
            String lawName,
            String lawCode,
            String docType,
            String sourceFileName,
            String fileHash,
            String versionNo,
            String status,
            OffsetDateTime now
    );

    void insertDocumentFile(
            Long id,
            Long docId,
            String storagePath,
            String mimeType,
            long fileSize,
            String sha256,
            String uploadBatchNo,
            OffsetDateTime now
    );

    Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now);

    Optional<FileDetail> findFileDetail(Long docId);
}
