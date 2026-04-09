package my.inspectorrag.docanalyzing.application.service;

import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParseApplicationServiceTest {

    @Mock
    private ParseRepository parseRepository;
    @Mock
    private ChunkingService chunkingService;

    @Test
    void parseShouldWriteChunksAndCreateEmbedTask() throws Exception {
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService);
        Path temp = Files.createTempFile("law", ".txt");
        Files.writeString(temp, "第1条 测试内容");

        when(parseRepository.findPrimaryStoragePath(1L)).thenReturn(Optional.of(temp.toString()));
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
        ParseApplicationService service = new ParseApplicationService(parseRepository, chunkingService);
        when(parseRepository.findPrimaryStoragePath(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.parse(new ParseTaskCommand(100L, 2L)));

        verify(parseRepository).updateParseStatus(2L, "failed");
        verify(parseRepository).markTaskStatus(eq(100L), eq("failed"), anyString());
    }
}
