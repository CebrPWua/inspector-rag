package my.inspectorrag.embedding.infrastructure.persistence.repository;

import my.inspectorrag.embedding.domain.model.EmbedExecution;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.model.value.ChunkId;
import my.inspectorrag.embedding.domain.model.value.DocumentId;
import my.inspectorrag.embedding.domain.model.value.EmbeddingStatus;
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
    public void markTaskStarted(EmbedExecution execution) {
        commandMapper.markTaskStatus(
                execution.taskId().value(),
                execution.taskStatus().dbValue(),
                null
        );
    }

    @Override
    public void markTaskCompleted(EmbedExecution execution) {
        commandMapper.markTaskStatus(
                execution.taskId().value(),
                execution.taskStatus().dbValue(),
                null
        );
    }

    @Override
    public void markTaskFailed(EmbedExecution execution) {
        commandMapper.markTaskStatus(
                execution.taskId().value(),
                execution.taskStatus().dbValue(),
                execution.errorMessage()
        );
    }

    @Override
    public List<PendingChunk> findPendingChunks(DocumentId docId, int limit) {
        return queryMapper.findPendingChunks(docId.value(), limit).stream()
                .map(row -> new PendingChunk(
                        ChunkId.of(row.chunkId()),
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
    public void markChunkStatus(ChunkId chunkId, EmbeddingStatus status) {
        commandMapper.markChunkStatus(chunkId.value(), status.dbValue());
    }
}
