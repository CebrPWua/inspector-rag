package my.inspectorrag.filemanagement.interfaces.dto;

import java.time.OffsetDateTime;

public record FileListItemResponse(
        String docId,
        String lawName,
        String lawCode,
        String docType,
        String versionNo,
        String status,
        String parseStatus,
        OffsetDateTime createdAt
) {
}
