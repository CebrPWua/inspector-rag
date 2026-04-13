package my.inspectorrag.filemanagement.domain.model;

import java.time.OffsetDateTime;

public record FileListItem(
        Long docId,
        String lawName,
        String lawCode,
        String docType,
        String versionNo,
        String status,
        String parseStatus,
        OffsetDateTime createdAt
) {
}
