package my.inspectorrag.docanalyzing.application.service;

import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import my.inspectorrag.docanalyzing.domain.service.DoclingGateway;
import my.inspectorrag.docanalyzing.domain.service.SourceDocumentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParseApplicationServiceTest {

    @Mock
    private ParseRepository parseRepository;
    @Mock
    private ChunkingService chunkingService;
    @Mock
    private SourceDocumentGateway sourceDocumentGateway;
    @Mock
    private DoclingGateway doclingGateway;

    @Test
    void parseShouldWriteChunksAndCreateEmbedTask() throws Exception {
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService, sourceDocumentGateway, doclingGateway);
        Path temp = Files.createTempFile("law", ".txt");
        Files.writeString(temp, "第1条 测试内容");

        when(parseRepository.findPrimaryStoragePath(1L)).thenReturn(Optional.of(temp.toString()));
        when(sourceDocumentGateway.read(temp.toString())).thenReturn(Files.readAllBytes(temp));
        when(chunkingService.splitToChunks(anyString())).thenReturn(List.of(
                new ParsedChunk("", "", "第1条", "", "第1条 测试内容", 1, "hash")
        ));
        when(parseRepository.createImportTask(anyLong(), eq(1L), eq("embed"), any())).thenReturn(888L);

        var response = service.parse(new ParseTaskCommand(99L, 1L));

        assertEquals(99L, response.taskId());
        assertEquals(1, response.chunkCount());
        assertEquals(888L, response.embedTaskId());
        verify(parseRepository).updateParseStatus(1L, "success");
        verify(parseRepository).markTaskStatus(99L, "success", null);
    }

    @Test
    void parseShouldFailWhenStoragePathMissing() {
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService, sourceDocumentGateway, doclingGateway);
        when(parseRepository.findPrimaryStoragePath(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.parse(new ParseTaskCommand(100L, 2L)));

        verify(parseRepository).updateParseStatus(2L, "failed");
        verify(parseRepository).markTaskStatus(eq(100L), eq("failed"), anyString());
    }

    @Test
    void parseShouldSupportDocxFile() throws Exception {
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService, sourceDocumentGateway, doclingGateway);
        Path docx = Files.createTempFile("law", ".docx");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(docx))) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("""
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>第1条 施工单位应建立制度。</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.getBytes());
            zos.closeEntry();
        }

        when(parseRepository.findPrimaryStoragePath(3L)).thenReturn(Optional.of(docx.toString()));
        when(sourceDocumentGateway.read(docx.toString())).thenReturn(Files.readAllBytes(docx));
        when(chunkingService.splitToChunks(anyString())).thenReturn(List.of(
                new ParsedChunk("", "", "第1条", "", "第1条 施工单位应建立制度。", 1, "hash")
        ));
        when(parseRepository.createImportTask(anyLong(), eq(3L), eq("embed"), any())).thenReturn(889L);

        var response = service.parse(new ParseTaskCommand(101L, 3L));

        assertEquals(1, response.chunkCount());
        assertEquals(889L, response.embedTaskId());
        verify(chunkingService).splitToChunks(contains("第1条 施工单位应建立制度。"));
    }

    @Test
    void parseShouldFallbackToDoclingWhenPdfTextUnavailable() {
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService, sourceDocumentGateway, doclingGateway);
        String storagePath = "s3://rag/laws/4/sample.pdf";

        when(parseRepository.findPrimaryStoragePath(4L)).thenReturn(Optional.of(storagePath));
        when(sourceDocumentGateway.read(storagePath)).thenReturn(new byte[]{0x01, 0x02, 0x03});
        when(doclingGateway.extractText(any(), eq("sample.pdf"))).thenReturn("第2条 监督范围包括施工和监理。");
        when(chunkingService.splitToChunks(contains("第2条 监督范围包括施工和监理。"))).thenReturn(List.of(
                new ParsedChunk("", "", "第2条", "", "第2条 监督范围包括施工和监理。", 1, "hash")
        ));
        when(parseRepository.createImportTask(anyLong(), eq(4L), eq("embed"), any())).thenReturn(890L);

        var response = service.parse(new ParseTaskCommand(102L, 4L));

        assertEquals(1, response.chunkCount());
        assertEquals(890L, response.embedTaskId());
        verify(doclingGateway).extractText(any(), eq("sample.pdf"));
    }
}
