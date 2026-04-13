package my.inspectorrag.filemanagement.infrastructure.persistence.mapper;

import java.time.OffsetDateTime;

public record FileListItemRow(
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
