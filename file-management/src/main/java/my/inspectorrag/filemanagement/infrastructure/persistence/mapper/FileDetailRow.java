package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record FileDetailRow(
        Long docId,
        String lawName,
        String lawCode,
        String docType,
        String versionNo,
        String status,
        String parseStatus,
        String sourceFileName,
        String fileHash,
        Long fileId,
        String storagePath,
        String mimeType,
        Long fileSizeBytes,
        String fileSha256,
        String uploadBatchNo,
        OffsetDateTime createdAt
) {
}
