package my.inspectorrag.docanalyzing.domain.repository;

import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ParseRepository {

    Optional<String> findPrimaryStoragePath(Long docId);

    void updateParseStatus(Long docId, String parseStatus);

    void markTaskStatus(Long taskId, String status, String errorMessage);

    void deleteExistingChunks(Long docId);

    void insertChunk(Long id, Long docId, ParsedChunk chunk, OffsetDateTime now);

    void insertChunkTag(Long id, Long chunkId, String tagType, String tagValue, OffsetDateTime now);

    Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now);
}
