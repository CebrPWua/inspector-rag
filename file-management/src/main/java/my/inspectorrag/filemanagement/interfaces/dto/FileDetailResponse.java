package my.inspectorrag.filemanagement.interfaces.dto;

import java.time.OffsetDateTime;

public record FileDetailResponse(
        String docId,
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
