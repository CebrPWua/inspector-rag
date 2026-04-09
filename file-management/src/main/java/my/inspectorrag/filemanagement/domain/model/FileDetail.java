package my.inspectorrag.filemanagement.domain.model;

import java.time.OffsetDateTime;

public record FileDetail(
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
