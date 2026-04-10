package my.inspectorrag.embedding.infrastructure.gateway;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "inspector.embedding", name = "vector-store-sync-enabled", havingValue = "false", matchIfMissing = true)
public class NoopVectorIndexService implements VectorIndexService {

    @Override
    public void upsert(PendingChunk chunk) {
        // no-op
    }
}
