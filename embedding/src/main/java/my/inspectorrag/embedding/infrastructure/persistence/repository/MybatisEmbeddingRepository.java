package my.inspectorrag.embedding.infrastructure.persistence.repository;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import my.inspectorrag.embedding.infrastructure.persistence.mapper.EmbeddingCommandMapper;
import my.inspectorrag.embedding.infrastructure.persistence.mapper.EmbeddingQueryMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Primary
@Repository
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
}
