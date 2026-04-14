package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.model.FileListItem;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
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
    private DocumentRepository documentRepository;
    @Mock
    private ObjectStorageGateway objectStorageGateway;
    @Mock
    private FileHashService fileHashService;

    @Test
    void uploadShouldReturnDuplicateWhenFileHashExists() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash1");
        when(documentRepository.findDocIdByFileHash("hash1")).thenReturn(Optional.of(1001L));

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-1", "v1", "standard", "active"));

        assertTrue(response.duplicate());
        assertEquals("1001", response.docId());
        assertNull(response.parseTaskId());
        verify(documentRepository, never()).insertSourceDocument(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadShouldCreateDocumentAndParseTaskWhenNewFile() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash2");
        when(documentRepository.findDocIdByFileHash("hash2")).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentRepository.createImportTask(anyLong(), anyLong(), eq("parse"), any())).thenReturn(9001L);

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-2", "v1", "standard", "active"));

        assertFalse(response.duplicate());
        assertNotNull(response.docId());
        assertEquals("9001", response.parseTaskId());
        ArgumentCaptor<String> lawCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> versionNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRepository, times(1)).insertSourceDocument(
                anyLong(),
                anyString(),
                lawCodeCaptor.capture(),
                anyString(),
                anyString(),
                eq("hash2"),
                versionNoCaptor.capture(),
                anyString(),
                any()
        );
        assertEquals("LAW-2", lawCodeCaptor.getValue());
        assertEquals("v1", versionNoCaptor.getValue());
        verify(documentRepository, times(1)).insertDocumentFile(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void uploadShouldFallbackLawCodeToFileHashAndVersionToV1WhenMissing() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash3");
        when(documentRepository.findDocIdByFileHash("hash3")).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentRepository.createImportTask(anyLong(), anyLong(), eq("parse"), any())).thenReturn(9002L);

        service.upload(new UploadLawFileCommand(file, "法规", null, null, "standard", "active"));

        ArgumentCaptor<String> lawCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> versionNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRepository).insertSourceDocument(
                anyLong(),
                anyString(),
                lawCodeCaptor.capture(),
                anyString(),
                anyString(),
                eq("hash3"),
                versionNoCaptor.capture(),
                anyString(),
                any()
        );
        assertEquals("hash3", lawCodeCaptor.getValue());
        assertEquals("v1", versionNoCaptor.getValue());
    }

    @Test
    void uploadShouldFallbackWhenLawCodeAndVersionAreBlank() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash4");
        when(documentRepository.findDocIdByFileHash("hash4")).thenReturn(Optional.empty());
        when(objectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentRepository.createImportTask(anyLong(), anyLong(), eq("parse"), any())).thenReturn(9003L);

        service.upload(new UploadLawFileCommand(file, "法规", "   ", "   ", "standard", "active"));

        ArgumentCaptor<String> lawCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> versionNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRepository).insertSourceDocument(
                anyLong(),
                anyString(),
                lawCodeCaptor.capture(),
                anyString(),
                anyString(),
                eq("hash4"),
                versionNoCaptor.capture(),
                anyString(),
                any()
        );
        assertEquals("hash4", lawCodeCaptor.getValue());
        assertEquals("v1", versionNoCaptor.getValue());
    }

    @Test
    void getFileShouldMapDomainToDto() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.findFileDetail(77L)).thenReturn(Optional.of(
                new FileDetail(77L, "法规", "LAW", "standard", "v1", "active", "pending", "law.txt", "hash", "/tmp/law.txt", now)
        ));

        var dto = service.getFile(77L);
        assertEquals("77", dto.docId());
        assertEquals("法规", dto.lawName());
        assertEquals("LAW", dto.lawCode());
        assertEquals("standard", dto.docType());
        assertEquals(now, dto.createdAt());
    }

    @Test
    void listFilesShouldMapDomainToDto() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.listFiles(200)).thenReturn(List.of(
                new FileListItem(88L, "法规B", "LAW-B", "regulation", "v2", "active", "success", now)
        ));

        var list = service.listFiles(200);

        assertEquals(1, list.size());
        assertEquals("88", list.get(0).docId());
        assertEquals("regulation", list.get(0).docType());
        assertEquals("success", list.get(0).parseStatus());
    }

    @Test
    void deleteShouldFailWhenDocumentNotExists() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        when(documentRepository.findFileDetail(100L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteFile(100L));

        assertTrue(ex.getMessage().contains("document not found"));
        verify(documentRepository, never()).deleteVectorsByDocId(anyLong());
        verify(documentRepository, never()).deleteSourceDocument(anyLong());
    }

    @Test
    void deleteShouldFailWhenParseIsPendingOrProcessing() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.findFileDetail(101L)).thenReturn(Optional.of(
                new FileDetail(101L, "法规", "LAW", "standard", "v1", "active", "processing", "law.txt", "hash", "/tmp/law.txt", now)
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteFile(101L));

        assertTrue(ex.getMessage().contains("cannot be deleted"));
        verify(documentRepository, never()).deleteVectorsByDocId(anyLong());
        verify(documentRepository, never()).deleteSourceDocument(anyLong());
        verify(objectStorageGateway, never()).delete(anyString());
    }

    @Test
    void deleteShouldCleanupVectorsDocumentAndStorageWhenParseFinished() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.findFileDetail(102L)).thenReturn(Optional.of(
                new FileDetail(102L, "法规", "LAW", "standard", "v1", "active", "success", "law.txt", "hash", "/tmp/law.txt", now)
        ));
        when(documentRepository.deleteSourceDocument(102L)).thenReturn(1);

        service.deleteFile(102L);

        verify(documentRepository).deleteVectorsByDocId(102L);
        verify(documentRepository).deleteSourceDocument(102L);
        verify(objectStorageGateway).delete("/tmp/law.txt");
    }

    @Test
    void deleteShouldNotRollbackWhenStorageDeletionFails() {
        FileApplicationService service = new FileApplicationService(documentRepository, objectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.findFileDetail(103L)).thenReturn(Optional.of(
                new FileDetail(103L, "法规", "LAW", "standard", "v1", "active", "failed", "law.txt", "hash", "/tmp/law.txt", now)
        ));
        when(documentRepository.deleteSourceDocument(103L)).thenReturn(1);
        doThrow(new IllegalArgumentException("storage failed")).when(objectStorageGateway).delete("/tmp/law.txt");

        assertDoesNotThrow(() -> service.deleteFile(103L));
        verify(documentRepository).deleteVectorsByDocId(103L);
        verify(documentRepository).deleteSourceDocument(103L);
        verify(objectStorageGateway).delete("/tmp/law.txt");
    }
}
