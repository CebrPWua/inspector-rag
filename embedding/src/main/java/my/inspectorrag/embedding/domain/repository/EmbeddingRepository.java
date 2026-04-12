package my.inspectorrag.embedding.domain.repository;

import my.inspectorrag.embedding.domain.model.PendingChunk;

import java.time.OffsetDateTime;
import java.util.List;

public interface EmbeddingRepository {

    void markTaskStatus(Long taskId, String status, String errorMsg);

    Long ensureActiveEmbeddingModel(String modelName, String version, int dimension, String provider, OffsetDateTime now);

    List<PendingChunk> findPendingChunks(Long docId, int limit);

    void markChunkStatus(Long chunkId, String status);

    void upsertChunkEmbedding(Long id, Long chunkId, Long modelId, String embeddingVersion, String vectorLiteral, OffsetDateTime now);
}
