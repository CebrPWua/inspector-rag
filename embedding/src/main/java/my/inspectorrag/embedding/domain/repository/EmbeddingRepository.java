package my.inspectorrag.embedding.domain.repository;

import my.inspectorrag.embedding.domain.model.PendingChunk;

import java.util.List;

public interface EmbeddingRepository {

    void markTaskStatus(Long taskId, String status, String errorMsg);

    List<PendingChunk> findPendingChunks(Long docId, int limit);

    void markChunkStatus(Long chunkId, String status);
}
