package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.application.query.model.FileDetailView;
import my.inspectorrag.filemanagement.application.query.model.FileListItemView;
import my.inspectorrag.filemanagement.application.query.repository.FileQueryRepository;
import my.inspectorrag.filemanagement.domain.model.DocumentFile;
import my.inspectorrag.filemanagement.domain.model.LawDocument;
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
import my.inspectorrag.filemanagement.domain.model.value.UploadBatchNo;
import my.inspectorrag.filemanagement.domain.model.value.VersionNo;
import my.inspectorrag.filemanagement.domain.repository.DocumentAggregateRepository;
import my.inspectorrag.filemanagement.domain.service.FileHashService;
import my.inspectorrag.filemanagement.domain.service.ObjectStorageGateway;
import my.inspectorrag.filemanagement.interfaces.dto.UploadFileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileApplicationServiceTest {

    @Mock
    private DocumentAggregateRepository documentAggregateRepository;
    @Mock
    private FileQueryRepository fileQueryRepository;
    @Mock
    private ObjectStorageGateway objectStorageGateway;
    @Mock
    private FileHashService fileHashService;

    @Test
    void uploadShouldReturnDuplicateWhenFileHashExists() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn(hex('a'));
        when(documentAggregateRepository.findDocIdByFileHash(any(FileHash.class))).thenReturn(Optional.of(DocumentId.of(1001L)));

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-1", "v1", "standard", "active"));

        assertTrue(response.duplicate());
        assertEquals("1001", response.docId());
        assertNull(response.parseTaskId());
        verify(documentAggregateRepository, never()).saveForUpload(any(), any());
        verify(documentAggregateRepository, never()).createImportTask(anyLong(), any(DocumentId.class), anyString(), any());
    }

    @Test
    void uploadShouldCreateDocumentAndParseTaskWhenNewFile() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn(hex('b'));
        when(documentAggregateRepository.findDocIdByFileHash(any(FileHash.class))).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentAggregateRepository.createImportTask(anyLong(), any(DocumentId.class), eq("parse"), any())).thenReturn(9001L);

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-2", "v1", "standard", "active"));

        assertFalse(response.duplicate());
        assertNotNull(response.docId());
        assertEquals("9001", response.parseTaskId());

        ArgumentCaptor<LawDocument> aggregateCaptor = ArgumentCaptor.forClass(LawDocument.class);
        verify(documentAggregateRepository, times(1)).saveForUpload(aggregateCaptor.capture(), any());
        LawDocument saved = aggregateCaptor.getValue();
        assertEquals("LAW-2", saved.lawCode().value());
        assertEquals("v1", saved.versionNo().value());
        assertEquals(ParseStatus.PENDING, saved.parseStatus());
        assertNotNull(saved.primaryFile());
        assertEquals(hex('b'), saved.primaryFile().sha256().value());
    }

    @Test
    void uploadShouldFallbackLawCodeToFileHashAndVersionToV1WhenMissing() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn(hex('c'));
        when(documentAggregateRepository.findDocIdByFileHash(any(FileHash.class))).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentAggregateRepository.createImportTask(anyLong(), any(DocumentId.class), eq("parse"), any())).thenReturn(9002L);

        service.upload(new UploadLawFileCommand(file, "法规", null, null, "standard", "active"));

        ArgumentCaptor<LawDocument> aggregateCaptor = ArgumentCaptor.forClass(LawDocument.class);
        verify(documentAggregateRepository).saveForUpload(aggregateCaptor.capture(), any());
        LawDocument saved = aggregateCaptor.getValue();
        assertEquals(hex('c'), saved.lawCode().value());
        assertEquals("v1", saved.versionNo().value());
    }

    @Test
    void uploadShouldFallbackWhenLawCodeAndVersionAreBlank() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn(hex('d'));
        when(documentAggregateRepository.findDocIdByFileHash(any(FileHash.class))).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentAggregateRepository.createImportTask(anyLong(), any(DocumentId.class), eq("parse"), any())).thenReturn(9003L);

        service.upload(new UploadLawFileCommand(file, "法规", "   ", "   ", "standard", "active"));

        ArgumentCaptor<LawDocument> aggregateCaptor = ArgumentCaptor.forClass(LawDocument.class);
        verify(documentAggregateRepository).saveForUpload(aggregateCaptor.capture(), any());
        LawDocument saved = aggregateCaptor.getValue();
        assertEquals(hex('d'), saved.lawCode().value());
        assertEquals("v1", saved.versionNo().value());
    }

    @Test
    void getFileShouldMapQueryViewToDto() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(fileQueryRepository.findFileDetail(77L)).thenReturn(Optional.of(
                new FileDetailView(77L, "法规", "LAW", "standard", "v1", "active", "pending", "law.txt", hex('e'), "/tmp/law.txt", now)
        ));

        var dto = service.getFile(77L);
        assertEquals("77", dto.docId());
        assertEquals("法规", dto.lawName());
        assertEquals("LAW", dto.lawCode());
        assertEquals("standard", dto.docType());
        assertEquals(now, dto.createdAt());
    }

    @Test
    void listFilesShouldMapQueryViewToDto() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(fileQueryRepository.listFiles(200)).thenReturn(List.of(
                new FileListItemView(88L, "法规B", "LAW-B", "regulation", "v2", "active", "success", now)
        ));

        var list = service.listFiles(200);

        assertEquals(1, list.size());
        assertEquals("88", list.get(0).docId());
        assertEquals("regulation", list.get(0).docType());
        assertEquals("success", list.get(0).parseStatus());
    }

    @Test
    void deleteShouldFailWhenDocumentNotExists() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        when(documentAggregateRepository.findById(any(DocumentId.class))).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteFile(100L));

        assertTrue(ex.getMessage().contains("document not found"));
        verify(documentAggregateRepository, never()).deleteVectorsByDocId(any(DocumentId.class));
        verify(documentAggregateRepository, never()).deleteSourceDocument(any(DocumentId.class));
    }

    @Test
    void deleteShouldFailWhenParseIsPendingOrProcessing() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        when(documentAggregateRepository.findById(any(DocumentId.class))).thenReturn(Optional.of(
                aggregateWithStatus(101L, ParseStatus.PROCESSING, "/tmp/law.txt")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteFile(101L));

        assertTrue(ex.getMessage().contains("cannot be deleted"));
        verify(documentAggregateRepository, never()).deleteVectorsByDocId(any(DocumentId.class));
        verify(documentAggregateRepository, never()).deleteSourceDocument(any(DocumentId.class));
        verify(objectStorageGateway, never()).delete(anyString());
    }

    @Test
    void deleteShouldCleanupVectorsDocumentAndStorageWhenParseFinished() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        when(documentAggregateRepository.findById(any(DocumentId.class))).thenReturn(Optional.of(
                aggregateWithStatus(102L, ParseStatus.SUCCESS, "/tmp/law.txt")
        ));
        when(documentAggregateRepository.deleteSourceDocument(any(DocumentId.class))).thenReturn(1);

        service.deleteFile(102L);

        verify(documentAggregateRepository).deleteVectorsByDocId(DocumentId.of(102L));
        verify(documentAggregateRepository).deleteSourceDocument(DocumentId.of(102L));
        verify(objectStorageGateway).delete("/tmp/law.txt");
    }

    @Test
    void deleteShouldNotRollbackWhenStorageDeletionFails() {
        FileApplicationService service = new FileApplicationService(documentAggregateRepository, fileQueryRepository, objectStorageGateway, fileHashService);
        when(documentAggregateRepository.findById(any(DocumentId.class))).thenReturn(Optional.of(
                aggregateWithStatus(103L, ParseStatus.FAILED, "/tmp/law.txt")
        ));
        when(documentAggregateRepository.deleteSourceDocument(any(DocumentId.class))).thenReturn(1);
        doThrow(new IllegalArgumentException("storage failed")).when(objectStorageGateway).delete("/tmp/law.txt");

        assertDoesNotThrow(() -> service.deleteFile(103L));
        verify(documentAggregateRepository).deleteVectorsByDocId(DocumentId.of(103L));
        verify(documentAggregateRepository).deleteSourceDocument(DocumentId.of(103L));
        verify(objectStorageGateway).delete("/tmp/law.txt");
    }

    private LawDocument aggregateWithStatus(Long docId, ParseStatus parseStatus, String storagePath) {
        return LawDocument.rehydrate(
                DocumentId.of(docId),
                LawName.of("法规"),
                LawCode.of("LAW"),
                DocType.of("standard"),
                VersionNo.of("v1"),
                DocumentStatus.ACTIVE,
                parseStatus,
                SourceFileName.of("law.txt"),
                FileHash.of(hex('f')),
                DocumentFile.rehydrate(
                        9001L,
                        StoragePath.of(storagePath),
                        MimeType.of("text/plain"),
                        FileSizeBytes.of(3),
                        FileHash.of(hex('f')),
                        UploadBatchNo.ofNullable("batch-1")
                )
        );
    }

    private String hex(char c) {
        return String.valueOf(c).repeat(64);
    }
}
