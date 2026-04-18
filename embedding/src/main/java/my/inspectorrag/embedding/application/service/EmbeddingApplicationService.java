package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.domain.model.EmbedExecution;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.model.value.DocumentId;
import my.inspectorrag.embedding.domain.model.value.EmbeddingStatus;
import my.inspectorrag.embedding.domain.model.value.TaskId;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import my.inspectorrag.embedding.interfaces.dto.EmbedTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmbeddingApplicationService {

    private final EmbeddingRepository embeddingRepository;
    private final VectorIndexService vectorIndexService;

    public EmbeddingApplicationService(
            EmbeddingRepository embeddingRepository,
            VectorIndexService vectorIndexService
    ) {
        this.embeddingRepository = embeddingRepository;
        this.vectorIndexService = vectorIndexService;
    }

    @Transactional
    public EmbedTaskResponse embed(EmbedTaskCommand command) {
        var execution = EmbedExecution.create(
                TaskId.of(command.taskId()),
                DocumentId.of(command.docId())
        );
        embeddingRepository.markTaskStarted(execution);

        try {
            List<PendingChunk> chunks = embeddingRepository.findPendingChunks(execution.docId(), 500);

            int processed = 0;
            for (PendingChunk chunk : chunks) {
                embeddingRepository.markChunkStatus(chunk.chunkId(), EmbeddingStatus.PROCESSING);
                vectorIndexService.upsert(chunk);
                embeddingRepository.markChunkStatus(chunk.chunkId(), EmbeddingStatus.SUCCESS);
                processed++;
            }

            execution = execution.complete(processed);
            embeddingRepository.markTaskCompleted(execution);
            return new EmbedTaskResponse(command.taskId(), command.docId(), processed);
        } catch (Exception ex) {
            execution = execution.fail(ex.getMessage());
            embeddingRepository.markTaskFailed(execution);
            throw ex;
        }
    }
}
