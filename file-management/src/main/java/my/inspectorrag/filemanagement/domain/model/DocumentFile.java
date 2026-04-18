package my.inspectorrag.filemanagement.domain.model;

import my.inspectorrag.filemanagement.domain.model.value.FileHash;
import my.inspectorrag.filemanagement.domain.model.value.FileSizeBytes;
import my.inspectorrag.filemanagement.domain.model.value.MimeType;
import my.inspectorrag.filemanagement.domain.model.value.StoragePath;
import my.inspectorrag.filemanagement.domain.model.value.UploadBatchNo;

import java.util.Objects;

public record DocumentFile(
        Long id,
        StoragePath storagePath,
        MimeType mimeType,
        FileSizeBytes fileSizeBytes,
        FileHash sha256,
        UploadBatchNo uploadBatchNo
) {

    public DocumentFile {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("fileId must be positive when provided");
        }
        Objects.requireNonNull(storagePath, "storagePath must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");
        Objects.requireNonNull(fileSizeBytes, "fileSizeBytes must not be null");
        Objects.requireNonNull(sha256, "sha256 must not be null");
    }

    public static DocumentFile createPrimaryForUpload(
            Long id,
            StoragePath storagePath,
            MimeType mimeType,
            FileSizeBytes fileSizeBytes,
            FileHash sha256,
            UploadBatchNo uploadBatchNo
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("fileId must be positive");
        }
        return new DocumentFile(id, storagePath, mimeType, fileSizeBytes, sha256, uploadBatchNo);
    }

    public static DocumentFile rehydrate(
            Long id,
            StoragePath storagePath,
            MimeType mimeType,
            FileSizeBytes fileSizeBytes,
            FileHash sha256,
            UploadBatchNo uploadBatchNo
    ) {
        return new DocumentFile(id, storagePath, mimeType, fileSizeBytes, sha256, uploadBatchNo);
    }
}
