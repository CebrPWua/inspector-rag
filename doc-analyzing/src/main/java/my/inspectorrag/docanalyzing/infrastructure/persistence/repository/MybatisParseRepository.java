package my.inspectorrag.docanalyzing.infrastructure.persistence.repository;

import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.infrastructure.persistence.mapper.ParseCommandMapper;
import my.inspectorrag.docanalyzing.infrastructure.persistence.mapper.ParseQueryMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Primary
@Repository
public class MybatisParseRepository implements ParseRepository {

    private final ParseQueryMapper queryMapper;
    private final ParseCommandMapper commandMapper;

    public MybatisParseRepository(ParseQueryMapper queryMapper, ParseCommandMapper commandMapper) {
        this.queryMapper = queryMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    public Optional<String> findPrimaryStoragePath(Long docId) {
        return Optional.ofNullable(queryMapper.findPrimaryStoragePath(docId));
    }

    @Override
    public void updateParseStatus(Long docId, String parseStatus) {
        commandMapper.updateParseStatus(docId, parseStatus);
    }

    @Override
    public void markTaskStatus(Long taskId, String status, String errorMessage) {
        commandMapper.markTaskStatus(taskId, status, errorMessage);
    }

    @Override
    public void deleteExistingChunks(Long docId) {
        commandMapper.deleteChunkTagsByDocId(docId);
        commandMapper.deleteChunksByDocId(docId);
    }

    @Override
    public void insertChunk(Long id, Long docId, ParsedChunk chunk, OffsetDateTime now) {
        commandMapper.insertChunk(
                id,
                docId,
                chunk.chapterTitle(),
                chunk.sectionTitle(),
                chunk.articleNo(),
                chunk.itemNo(),
                chunk.content(),
                chunk.chunkSeq(),
                chunk.contentHash(),
                now
        );
    }

    @Override
    public void insertChunkTag(Long id, Long chunkId, String tagType, String tagValue, OffsetDateTime now) {
        commandMapper.insertChunkTag(id, chunkId, tagType, tagValue, now);
    }

    @Override
    public Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now) {
        commandMapper.insertImportTask(id, docId, taskType, now);
        return id;
    }
}
