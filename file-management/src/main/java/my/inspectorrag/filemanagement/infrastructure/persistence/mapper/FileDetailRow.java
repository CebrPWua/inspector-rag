package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record FileDetailRow(
        Long docId,
        String lawName,
        String lawCode,
        String versionNo,
        String status,
        String parseStatus,
        String sourceFileName,
        String fileHash,
        String storagePath,
        OffsetDateTime createdAt
) {
}
