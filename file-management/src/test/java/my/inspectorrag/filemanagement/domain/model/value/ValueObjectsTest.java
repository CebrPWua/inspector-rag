package my.inspectorrag.filemanagement.domain.model.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueObjectsTest {

    @Test
    void documentIdShouldRejectNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> DocumentId.of(0L));
        assertThrows(IllegalArgumentException.class, () -> DocumentId.of(-1L));
    }

    @Test
    void lawNameShouldTrimAndValidateLength() {
        LawName value = LawName.of("  建筑法规  ");
        assertEquals("建筑法规", value.value());
        assertThrows(IllegalArgumentException.class, () -> LawName.of("   "));
        assertThrows(IllegalArgumentException.class, () -> LawName.of("x".repeat(513)));
    }

    @Test
    void docTypeShouldRejectTooLong() {
        DocType value = DocType.of(" standard ");
        assertEquals("standard", value.value());
        assertThrows(IllegalArgumentException.class, () -> DocType.of("x".repeat(65)));
    }

    @Test
    void versionNoShouldDefaultToV1WhenBlank() {
        assertEquals("v1", VersionNo.ofOrDefault(null).value());
        assertEquals("v1", VersionNo.ofOrDefault("   ").value());
        assertEquals("v2", VersionNo.ofOrDefault(" v2 ").value());
    }

    @Test
    void documentStatusShouldParseStrictly() {
        assertEquals(DocumentStatus.ACTIVE, DocumentStatus.from("active"));
        assertEquals(DocumentStatus.PENDING_CONFIRM, DocumentStatus.from("pending_confirm"));
        assertThrows(IllegalArgumentException.class, () -> DocumentStatus.from("ACTIVE"));
    }

    @Test
    void parseStatusShouldExposeDeleteRule() {
        assertFalse(ParseStatus.PENDING.canDelete());
        assertFalse(ParseStatus.PROCESSING.canDelete());
        assertTrue(ParseStatus.SUCCESS.canDelete());
        assertTrue(ParseStatus.FAILED.canDelete());
        assertThrows(IllegalArgumentException.class, () -> ParseStatus.from("done"));
    }

    @Test
    void fileHashShouldRequireLowerHex64() {
        String valid = "a".repeat(64);
        assertEquals(valid, FileHash.of(valid).value());
        assertThrows(IllegalArgumentException.class, () -> FileHash.of("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> FileHash.of("abc"));
    }

    @Test
    void sourceFileNameStoragePathAndMimeTypeShouldValidate() {
        assertEquals("a.pdf", SourceFileName.of("  a.pdf ").value());
        assertEquals("/tmp/a.pdf", StoragePath.of(" /tmp/a.pdf ").value());
        assertEquals("text/plain", MimeType.of(" text/plain ").value());

        assertThrows(IllegalArgumentException.class, () -> SourceFileName.of(" "));
        assertThrows(IllegalArgumentException.class, () -> StoragePath.of(" "));
        assertThrows(IllegalArgumentException.class, () -> MimeType.of("x".repeat(128)));
    }

    @Test
    void fileSizeShouldBePositive() {
        assertEquals(1L, FileSizeBytes.of(1).value());
        assertThrows(IllegalArgumentException.class, () -> FileSizeBytes.of(0));
    }

    @Test
    void uploadBatchNoShouldSupportNullableAndLengthValidation() {
        assertNull(UploadBatchNo.ofNullable(null));
        assertNull(UploadBatchNo.ofNullable("   "));
        assertEquals("batch-1", UploadBatchNo.ofNullable(" batch-1 ").value());
        assertThrows(IllegalArgumentException.class, () -> UploadBatchNo.ofNullable("x".repeat(65)));
    }
}
