package my.inspectorrag.filemanagement.application.query.model;

import java.time.OffsetDateTime;

public record FileDetailView(
        Long docId,
        String lawName,
        String lawCode,
        String docType,
        String versionNo,
        String status,
        String parseStatus,
        String sourceFileName,
        String fileHash,
        String storagePath,
        OffsetDateTime createdAt
) {
}
