package my.inspectorrag.filemanagement.domain.model;

import my.inspectorrag.filemanagement.domain.model.value.DocType;
import my.inspectorrag.filemanagement.domain.model.value.DocumentId;
import my.inspectorrag.filemanagement.domain.model.value.DocumentStatus;
import my.inspectorrag.filemanagement.domain.model.value.FileHash;
import my.inspectorrag.filemanagement.domain.model.value.FileSizeBytes;
import my.inspectorrag.filemanagement.domain.model.value.LawCode;
import my.inspectorrag.filemanagement.domain.model.value.LawName;
import my.inspectorrag.filemanagement.domain.model.value.MimeType;
import my.inspectorrag.filemanagement.domain.model.value.ParseStatus;
import my.inspectorrag.filemanagement.domain.model.value.SourceFileName;
import my.inspectorrag.filemanagement.domain.model.value.StoragePath;
import my.inspectorrag.filemanagement.domain.model.value.VersionNo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LawDocumentTest {

    @Test
    void createForUploadShouldFallbackLawCodeAndVersionAndSetPending() {
        FileHash fileHash = FileHash.of("a".repeat(64));
        LawDocument aggregate = LawDocument.createForUpload(
                DocumentId.of(1001L),
                LawName.of("法规A"),
                "   ",
                DocType.of("standard"),
                SourceFileName.of("law.txt"),
                fileHash,
                null,
                DocumentStatus.ACTIVE,
                DocumentFile.createPrimaryForUpload(
                        2001L,
                        StoragePath.of("/tmp/law.txt"),
                        MimeType.of("text/plain"),
                        FileSizeBytes.of(32),
                        fileHash,
                        null
                )
        );

        assertEquals(fileHash.value(), aggregate.lawCode().value());
        assertEquals("v1", aggregate.versionNo().value());
        assertEquals(ParseStatus.PENDING, aggregate.parseStatus());
    }

    @Test
    void ensureDeletableShouldAllowOnlySuccessAndFailed() {
        LawDocument successDoc = rehydrateWith(ParseStatus.SUCCESS);
        LawDocument failedDoc = rehydrateWith(ParseStatus.FAILED);
        LawDocument pendingDoc = rehydrateWith(ParseStatus.PENDING);
        LawDocument processingDoc = rehydrateWith(ParseStatus.PROCESSING);

        assertDoesNotThrow(successDoc::ensureDeletable);
        assertDoesNotThrow(failedDoc::ensureDeletable);
        assertThrows(IllegalArgumentException.class, pendingDoc::ensureDeletable);
        assertThrows(IllegalArgumentException.class, processingDoc::ensureDeletable);
    }

    private LawDocument rehydrateWith(ParseStatus parseStatus) {
        return LawDocument.rehydrate(
                DocumentId.of(1001L),
                LawName.of("法规A"),
                LawCode.of("LAW-1"),
                DocType.of("standard"),
                VersionNo.of("v1"),
                DocumentStatus.ACTIVE,
                parseStatus,
                SourceFileName.of("law.txt"),
                FileHash.of("a".repeat(64)),
                DocumentFile.rehydrate(
                        2001L,
                        StoragePath.of("/tmp/law.txt"),
                        MimeType.of("text/plain"),
                        FileSizeBytes.of(32),
                        FileHash.of("a".repeat(64)),
                        null
                )
        );
    }
}
