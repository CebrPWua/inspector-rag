package my.inspectorrag.docanalyzing.infrastructure.persistence.repository;

import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcParseRepository implements ParseRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcParseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findPrimaryStoragePath(Long docId) {
        return jdbcTemplate.query(
                "select storage_path from ingest.document_file where doc_id = ? and is_primary = true limit 1",
                (rs, rowNum) -> rs.getString("storage_path"),
                docId
        ).stream().findFirst();
    }

    @Override
    public void updateParseStatus(Long docId, String parseStatus) {
        jdbcTemplate.update("update ingest.source_document set parse_status = ? where id = ?", parseStatus, docId);
    }

    @Override
    public void markTaskStatus(Long taskId, String status, String errorMessage) {
        jdbcTemplate.update(
                """
                        update ops.import_task
                           set task_status = ?,
                               error_msg = ?,
                               finished_at = case when ? in ('success', 'failed') then now() else finished_at end
                         where id = ?
                        """,
                status,
                errorMessage,
                status,
                taskId
        );
    }

    @Override
    public void deleteExistingChunks(Long docId) {
        jdbcTemplate.update("delete from ingest.chunk_tag where chunk_id in (select id from ingest.law_chunk where doc_id = ?)", docId);
        jdbcTemplate.update("delete from ingest.law_chunk where doc_id = ?", docId);
    }

    @Override
    public void insertChunk(Long id, Long docId, ParsedChunk chunk, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ingest.law_chunk
                        (id, doc_id, chapter_title, section_title, article_no, item_no, content, chunk_seq, content_hash, embedding_status, status, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'active', ?, ?)
                        """,
                id,
                docId,
                chunk.chapterTitle(),
                chunk.sectionTitle(),
                chunk.articleNo(),
                chunk.itemNo(),
                chunk.content(),
                chunk.chunkSeq(),
                chunk.contentHash(),
                now,
                now
        );
    }

    @Override
    public void insertChunkTag(Long id, Long chunkId, String tagType, String tagValue, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into ingest.chunk_tag(id, chunk_id, tag_type, tag_value, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
                id,
                chunkId,
                tagType,
                tagValue,
                now,
                now
        );
    }

    @Override
    public Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into ops.import_task(id, doc_id, task_type, task_status, retry_count, max_retry, created_at, updated_at) values (?, ?, ?, 'pending', 0, 3, ?, ?)",
                id,
                docId,
                taskType,
                now,
                now
        );
        return id;
    }
}
