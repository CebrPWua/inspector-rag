package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
import my.inspectorrag.filemanagement.domain.service.FileHashService;
import my.inspectorrag.filemanagement.infrastructure.gateway.LocalObjectStorageGateway;
import my.inspectorrag.filemanagement.interfaces.dto.UploadFileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileApplicationServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private LocalObjectStorageGateway localObjectStorageGateway;
    @Mock
    private FileHashService fileHashService;

    @Test
    void uploadShouldReturnDuplicateWhenFileHashExists() {
        FileApplicationService service = new FileApplicationService(documentRepository, localObjectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash1");
        when(documentRepository.findDocIdByFileHash("hash1")).thenReturn(Optional.of(1001L));

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-1", "v1", "standard", "active"));

        assertTrue(response.duplicate());
        assertEquals(1001L, response.docId());
        assertNull(response.parseTaskId());
        verify(documentRepository, never()).insertSourceDocument(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uploadShouldCreateDocumentAndParseTaskWhenNewFile() {
        FileApplicationService service = new FileApplicationService(documentRepository, localObjectStorageGateway, fileHashService);
        MockMultipartFile file = new MockMultipartFile("file", "law.txt", "text/plain", "abc".getBytes());

        when(fileHashService.sha256(any())).thenReturn("hash2");
        when(documentRepository.findDocIdByFileHash("hash2")).thenReturn(Optional.empty());
        when(localObjectStorageGateway.save(anyLong(), anyString(), any())).thenReturn("/tmp/f-law.txt");
        when(documentRepository.createImportTask(anyLong(), anyLong(), eq("parse"), any())).thenReturn(9001L);

        UploadFileResponse response = service.upload(new UploadLawFileCommand(file, "法规", "LAW-2", "v1", "standard", "active"));

        assertFalse(response.duplicate());
        assertNotNull(response.docId());
        assertEquals(9001L, response.parseTaskId());
        verify(documentRepository, times(1)).insertSourceDocument(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(documentRepository, times(1)).insertDocumentFile(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void getFileShouldMapDomainToDto() {
        FileApplicationService service = new FileApplicationService(documentRepository, localObjectStorageGateway, fileHashService);
        OffsetDateTime now = OffsetDateTime.now();
        when(documentRepository.findFileDetail(77L)).thenReturn(Optional.of(
                new FileDetail(77L, "法规", "LAW", "v1", "active", "pending", "law.txt", "hash", "/tmp/law.txt", now)
        ));

        var dto = service.getFile(77L);
        assertEquals(77L, dto.docId());
        assertEquals("法规", dto.lawName());
        assertEquals("LAW", dto.lawCode());
        assertEquals(now, dto.createdAt());
    }
}
