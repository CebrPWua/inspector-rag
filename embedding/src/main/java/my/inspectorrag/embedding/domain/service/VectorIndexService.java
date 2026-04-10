package my.inspectorrag.embedding.domain.service;

import my.inspectorrag.embedding.domain.model.PendingChunk;

public interface VectorIndexService {

    void upsert(PendingChunk chunk);
}
