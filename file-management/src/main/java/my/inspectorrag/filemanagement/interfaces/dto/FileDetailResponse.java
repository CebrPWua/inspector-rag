package my.inspectorrag.filemanagement.interfaces.dto;

import java.time.OffsetDateTime;

public record FileDetailResponse(
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
