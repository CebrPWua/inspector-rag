package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.domain.model.EmbedExecution;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.model.value.ChunkId;
import my.inspectorrag.embedding.domain.model.value.EmbeddingStatus;
import my.inspectorrag.embedding.domain.model.value.TaskStatus;
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

        when(embeddingRepository.findPendingChunks(any(), eq(500))).thenReturn(List.of(
                new PendingChunk(ChunkId.of(10L), "法规", "章", "节", "第1条", "正文", 1, 1, "v1", "active")
        ));

        var response = service.embed(new EmbedTaskCommand(99L, 1L));

        assertEquals(99L, response.taskId());
        assertEquals(1L, response.docId());
        assertEquals(1, response.processedChunks());
        verify(vectorIndexService).upsert(any());
        verify(embeddingRepository).markChunkStatus(eq(ChunkId.of(10L)), eq(EmbeddingStatus.SUCCESS));
        verify(embeddingRepository).markTaskCompleted(argThat(exec ->
                exec.taskStatus() == TaskStatus.SUCCESS && exec.processedChunks() == 1));
    }

    @Test
    void embedShouldMarkTaskFailedWhenError() {
        EmbeddingApplicationService service = new EmbeddingApplicationService(
                embeddingRepository,
                vectorIndexService
        );

        when(embeddingRepository.findPendingChunks(any(), eq(500))).thenReturn(List.of(
                new PendingChunk(ChunkId.of(10L), "法规", "章", "节", "第1条", "正文", 1, 1, "v1", "active")
        ));
        doThrow(new IllegalStateException("mock fail")).when(vectorIndexService).upsert(any());

        assertThrows(IllegalStateException.class, () -> service.embed(new EmbedTaskCommand(99L, 1L)));
        verify(embeddingRepository).markTaskFailed(argThat(exec ->
                exec.taskStatus() == TaskStatus.FAILED && exec.errorMessage().contains("mock fail")));
    }
}
