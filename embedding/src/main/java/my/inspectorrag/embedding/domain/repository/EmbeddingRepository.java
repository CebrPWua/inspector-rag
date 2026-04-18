package my.inspectorrag.embedding.domain.repository;

import my.inspectorrag.embedding.domain.model.EmbedExecution;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.model.value.ChunkId;
import my.inspectorrag.embedding.domain.model.value.DocumentId;
import my.inspectorrag.embedding.domain.model.value.EmbeddingStatus;

import java.util.List;

public interface EmbeddingRepository {

    void markTaskStarted(EmbedExecution execution);

    void markTaskCompleted(EmbedExecution execution);

    void markTaskFailed(EmbedExecution execution);

    List<PendingChunk> findPendingChunks(DocumentId docId, int limit);

    void markChunkStatus(ChunkId chunkId, EmbeddingStatus status);
}
