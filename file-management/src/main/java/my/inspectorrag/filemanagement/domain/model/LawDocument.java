package my.inspectorrag.filemanagement.domain.model;

import my.inspectorrag.filemanagement.domain.model.value.DocType;
import my.inspectorrag.filemanagement.domain.model.value.DocumentId;
import my.inspectorrag.filemanagement.domain.model.value.DocumentStatus;
import my.inspectorrag.filemanagement.domain.model.value.FileHash;
import my.inspectorrag.filemanagement.domain.model.value.LawCode;
import my.inspectorrag.filemanagement.domain.model.value.LawName;
import my.inspectorrag.filemanagement.domain.model.value.ParseStatus;
import my.inspectorrag.filemanagement.domain.model.value.SourceFileName;
import my.inspectorrag.filemanagement.domain.model.value.VersionNo;

import java.util.Objects;
import java.util.Optional;

public record LawDocument(
        DocumentId id,
        LawName lawName,
        LawCode lawCode,
        DocType docType,
        VersionNo versionNo,
        DocumentStatus status,
        ParseStatus parseStatus,
        SourceFileName sourceFileName,
        FileHash fileHash,
        DocumentFile primaryFile
) {

    public LawDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(lawName, "lawName must not be null");
        Objects.requireNonNull(lawCode, "lawCode must not be null");
        Objects.requireNonNull(docType, "docType must not be null");
        Objects.requireNonNull(versionNo, "versionNo must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(parseStatus, "parseStatus must not be null");
        Objects.requireNonNull(sourceFileName, "sourceFileName must not be null");
        Objects.requireNonNull(fileHash, "fileHash must not be null");
    }

    public static LawDocument createForUpload(
            DocumentId id,
            LawName lawName,
            String rawLawCode,
            DocType docType,
            SourceFileName sourceFileName,
            FileHash fileHash,
            String rawVersionNo,
            DocumentStatus status,
            DocumentFile primaryFile
    ) {
        Objects.requireNonNull(primaryFile, "primaryFile must not be null");
        return new LawDocument(
                id,
                lawName,
                LawCode.ofOrFallback(rawLawCode, fileHash),
                docType,
                VersionNo.ofOrDefault(rawVersionNo),
                status,
                ParseStatus.PENDING,
                sourceFileName,
                fileHash,
                primaryFile
        );
    }

    public static LawDocument rehydrate(
            DocumentId id,
            LawName lawName,
            LawCode lawCode,
            DocType docType,
            VersionNo versionNo,
            DocumentStatus status,
            ParseStatus parseStatus,
            SourceFileName sourceFileName,
            FileHash fileHash,
            DocumentFile primaryFile
    ) {
        return new LawDocument(
                id,
                lawName,
                lawCode,
                docType,
                versionNo,
                status,
                parseStatus,
                sourceFileName,
                fileHash,
                primaryFile
        );
    }

    public void ensureDeletable() {
        if (!parseStatus.canDelete()) {
            throw new IllegalArgumentException("document is being parsed and cannot be deleted: " + id.value());
        }
    }

    public Optional<DocumentFile> primaryFileOptional() {
        return Optional.ofNullable(primaryFile);
    }
}
