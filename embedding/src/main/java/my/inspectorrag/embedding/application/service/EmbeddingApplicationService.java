package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.domain.model.PendingChunk;
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
        embeddingRepository.markTaskStatus(command.taskId(), "processing", null);

        try {
            List<PendingChunk> chunks = embeddingRepository.findPendingChunks(command.docId(), 500);

            int processed = 0;
            for (PendingChunk chunk : chunks) {
                embeddingRepository.markChunkStatus(chunk.chunkId(), "processing");
                vectorIndexService.upsert(chunk);
                embeddingRepository.markChunkStatus(chunk.chunkId(), "success");
                processed++;
            }

            embeddingRepository.markTaskStatus(command.taskId(), "success", null);
            return new EmbedTaskResponse(command.taskId(), command.docId(), processed);
        } catch (Exception ex) {
            embeddingRepository.markTaskStatus(command.taskId(), "failed", ex.getMessage());
            throw ex;
        }
    }
}
