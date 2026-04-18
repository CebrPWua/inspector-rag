package my.inspectorrag.filemanagement.application.query.model;

import java.time.OffsetDateTime;

public record FileListItemView(
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
