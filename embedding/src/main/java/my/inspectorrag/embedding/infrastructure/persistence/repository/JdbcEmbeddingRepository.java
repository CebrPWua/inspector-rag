package my.inspectorrag.embedding.infrastructure.persistence.repository;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcEmbeddingRepository implements EmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void markTaskStatus(Long taskId, String status, String errorMsg) {
        jdbcTemplate.update(
                """
                        update ops.import_task
                           set task_status = ?,
                               error_msg = ?,
                               finished_at = case when ? in ('success', 'failed') then now() else finished_at end
                         where id = ?
                        """,
                status,
                errorMsg,
                status,
                taskId
        );
    }

    @Override
    public Long ensureActiveEmbeddingModel(String modelName, String version, int dimension, OffsetDateTime now) {
        Long existing = jdbcTemplate.query(
                "select id from indexing.embedding_model where model_name = ? and version = ? limit 1",
                (rs, rowNum) -> rs.getLong("id"),
                modelName,
                version
        ).stream().findFirst().orElse(null);
        if (existing != null) {
            jdbcTemplate.update("update indexing.embedding_model set is_active = true where id = ?", existing);
            return existing;
        }

        Long id = newId();
        jdbcTemplate.update(
                """
                        insert into indexing.embedding_model(id, model_name, dimension, version, provider, is_active, created_at, updated_at)
                        values (?, ?, ?, ?, 'mock', true, ?, ?)
                        on conflict (model_name, version)
                        do update set is_active = excluded.is_active
                        """,
                id,
                modelName,
                dimension,
                version,
                now,
                now
        );

        return jdbcTemplate.queryForObject(
                "select id from indexing.embedding_model where model_name = ? and version = ?",
                Long.class,
                modelName,
                version
        );
    }

    @Override
    public List<PendingChunk> findPendingChunks(Long docId, int limit) {
        return jdbcTemplate.query(
                """
                        select lc.id as chunk_id,
                               sd.law_name,
                               lc.chapter_title,
                               lc.section_title,
                               lc.article_no,
                               lc.content,
                               lc.page_start,
                               lc.page_end,
                               sd.version_no,
                               lc.status
                          from ingest.law_chunk lc
                          join ingest.source_document sd on sd.id = lc.doc_id
                         where lc.doc_id = ?
                           and lc.embedding_status = 'pending'
                         order by lc.id
                         limit ?
                        """,
                (rs, rowNum) -> new PendingChunk(
                        rs.getLong("chunk_id"),
                        rs.getString("law_name"),
                        rs.getString("chapter_title"),
                        rs.getString("section_title"),
                        rs.getString("article_no"),
                        rs.getString("content"),
                        rs.getObject("page_start", Integer.class),
                        rs.getObject("page_end", Integer.class),
                        rs.getString("version_no"),
                        rs.getString("status")
                ),
                docId,
                limit
        );
    }

    @Override
    public void markChunkStatus(Long chunkId, String status) {
        jdbcTemplate.update("update ingest.law_chunk set embedding_status = ? where id = ?", status, chunkId);
    }

    @Override
    public void upsertChunkEmbedding(Long id, Long chunkId, Long modelId, String embeddingVersion, String vectorLiteral, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into indexing.law_chunk_embedding
                        (id, chunk_id, model_id, embedding_version, embedding, created_at, updated_at)
                        values (?, ?, ?, ?, ?::vector, ?, ?)
                        on conflict (chunk_id, model_id, embedding_version)
                        do update set embedding = excluded.embedding,
                                      updated_at = excluded.updated_at
                        """,
                id,
                chunkId,
                modelId,
                embeddingVersion,
                vectorLiteral,
                now,
                now
        );
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
