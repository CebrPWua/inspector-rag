package my.inspectorrag.embedding.infrastructure.persistence.repository;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import my.inspectorrag.embedding.infrastructure.persistence.mapper.EmbeddingCommandMapper;
import my.inspectorrag.embedding.infrastructure.persistence.mapper.EmbeddingQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Primary
@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "mybatis")
public class MybatisEmbeddingRepository implements EmbeddingRepository {

    private final EmbeddingCommandMapper commandMapper;
    private final EmbeddingQueryMapper queryMapper;

    public MybatisEmbeddingRepository(EmbeddingCommandMapper commandMapper, EmbeddingQueryMapper queryMapper) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
    }

    @Override
    public void markTaskStatus(Long taskId, String status, String errorMsg) {
        commandMapper.markTaskStatus(taskId, status, errorMsg);
    }

    @Override
    public Long ensureActiveEmbeddingModel(String modelName, String version, int dimension, OffsetDateTime now) {
        Long existing = queryMapper.findEmbeddingModelId(modelName, version);
        if (existing != null) {
            commandMapper.activateEmbeddingModel(existing);
            return existing;
        }

        Long id = newId();
        commandMapper.insertOrActivateEmbeddingModel(id, modelName, version, dimension, now);
        return queryMapper.requireEmbeddingModelId(modelName, version);
    }

    @Override
    public List<PendingChunk> findPendingChunks(Long docId, int limit) {
        return queryMapper.findPendingChunks(docId, limit).stream()
                .map(row -> new PendingChunk(
                        row.chunkId(),
                        row.lawName(),
                        row.chapterTitle(),
                        row.sectionTitle(),
                        row.articleNo(),
                        row.content(),
                        row.pageStart(),
                        row.pageEnd(),
                        row.versionNo(),
                        row.status()
                ))
                .toList();
    }

    @Override
    public void markChunkStatus(Long chunkId, String status) {
        commandMapper.markChunkStatus(chunkId, status);
    }

    @Override
    public void upsertChunkEmbedding(Long id, Long chunkId, Long modelId, String embeddingVersion, String vectorLiteral, OffsetDateTime now) {
        commandMapper.upsertChunkEmbedding(id, chunkId, modelId, embeddingVersion, vectorLiteral, now);
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
