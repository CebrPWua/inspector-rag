package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingApplicationServiceTest {

    @Mock
    private EmbeddingRepository embeddingRepository;
    @Mock
    private VectorIndexService vectorIndexService;

    @Test
    void embedShouldProcessPendingChunks() {
        EmbeddingApplicationService service = new EmbeddingApplicationService(
                embeddingRepository,
                vectorIndexService
        );

        when(embeddingRepository.findPendingChunks(1L, 500)).thenReturn(List.of(
                new PendingChunk(10L, "法规", "章", "节", "第1条", "正文", 1, 1, "v1", "active")
        ));

        var response = service.embed(new EmbedTaskCommand(99L, 1L));

        assertEquals(99L, response.taskId());
        assertEquals(1L, response.docId());
        assertEquals(1, response.processedChunks());
        verify(vectorIndexService).upsert(any());
        verify(embeddingRepository).markChunkStatus(10L, "success");
        verify(embeddingRepository).markTaskStatus(99L, "success", null);
    }

    @Test
    void embedShouldMarkTaskFailedWhenError() {
        EmbeddingApplicationService service = new EmbeddingApplicationService(
                embeddingRepository,
                vectorIndexService
        );

        when(embeddingRepository.findPendingChunks(1L, 500)).thenReturn(List.of(
                new PendingChunk(10L, "法规", "章", "节", "第1条", "正文", 1, 1, "v1", "active")
        ));
        doThrow(new IllegalStateException("mock fail")).when(vectorIndexService).upsert(any());

        assertThrows(IllegalStateException.class, () -> service.embed(new EmbedTaskCommand(99L, 1L)));
        verify(embeddingRepository).markTaskStatus(eq(99L), eq("failed"), contains("mock fail"));
    }
}
