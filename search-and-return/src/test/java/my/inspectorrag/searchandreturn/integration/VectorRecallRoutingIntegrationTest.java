package my.inspectorrag.searchandreturn.integration;

import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.infrastructure.gateway.SpringAiVectorStoreRecallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
class VectorRecallRoutingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("inspector_rag")
            .withUsername("postgres")
            .withPassword("postgres");

    private JdbcTemplate jdbcTemplate;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("create schema if not exists indexing");
        jdbcTemplate.execute("drop table if exists indexing.rag_chunk_vec_qwen3_2048_v1");
        jdbcTemplate.execute("drop table if exists indexing.rag_chunk_vec_qwen3_4096_v1");
        jdbcTemplate.execute("drop table if exists indexing.embedding_profile");
        jdbcTemplate.execute("""
                create table indexing.embedding_profile (
                    id bigint primary key,
                    profile_key varchar(64) not null unique,
                    provider varchar(64) not null,
                    model_name varchar(128) not null,
                    dimension int not null,
                    vector_type varchar(32) not null,
                    distance_metric varchar(32) not null,
                    storage_table varchar(128) not null,
                    read_enabled boolean not null,
                    write_enabled boolean not null,
                    is_default_read boolean not null,
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """);
        embeddingModel = Mockito.mock(EmbeddingModel.class);
    }

    @Test
    void recallShouldProjectTo2048AndReadDefaultProfile() {
        jdbcTemplate.execute("""
                create table indexing.rag_chunk_vec_qwen3_2048_v1 (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding halfvec(2048)
                )
                """);
        jdbcTemplate.update("""
                insert into indexing.embedding_profile
                (id, profile_key, provider, model_name, dimension, vector_type, distance_metric, storage_table, read_enabled, write_enabled, is_default_read)
                values (1, 'qwen3_2048_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 2048, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_2048_v1', true, true, true)
                """);
        jdbcTemplate.update(
                """
                insert into indexing.rag_chunk_vec_qwen3_2048_v1(id, content, metadata, embedding)
                values (?, ?, cast(? as jsonb), cast(? as halfvec(2048)))
                """,
                "1001",
                "内容A",
                "{\"chunkId\":1001,\"lawName\":\"法规A\",\"articleNo\":\"第1条\",\"content\":\"内容A\",\"versionNo\":\"v1\"}",
                literal(2048, 0, 1.0f)
        );
        when(embeddingModel.embed(anyString())).thenReturn(vector(4096, 0, 1.0f));

        SpringAiVectorStoreRecallService service = new SpringAiVectorStoreRecallService(
                jdbcTemplate, embeddingModel, "default", 0, "", 8
        );
        var result = service.recall("测试问题", 5, QaFilters.empty(), "conv-1");
        assertFalse(result.isEmpty());
        assertEquals(1001L, result.getFirst().chunkId());
    }

    @Test
    void recallShouldUseQuantizedTwoStageFor4096Profile() {
        jdbcTemplate.execute("""
                create table indexing.rag_chunk_vec_qwen3_4096_v1 (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding halfvec(4096)
                )
                """);
        jdbcTemplate.execute("""
                create index if not exists idx_rag_chunk_vec_qwen3_4096_v1_hnsw_bq
                    on indexing.rag_chunk_vec_qwen3_4096_v1 using hnsw ((binary_quantize(embedding)::bit(4096)) bit_hamming_ops)
                """);
        jdbcTemplate.update("""
                insert into indexing.embedding_profile
                (id, profile_key, provider, model_name, dimension, vector_type, distance_metric, storage_table, read_enabled, write_enabled, is_default_read)
                values (2, 'qwen3_4096_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 4096, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_4096_v1', true, true, true)
                """);

        insert4096("2001", "法规X", literal(4096, 0, 1.0f));
        insert4096("2002", "法规Y", literal(4096, 0, -1.0f));
        insert4096("2003", "法规Z", literal(4096, 0, 0.2f));

        when(embeddingModel.embed(anyString())).thenReturn(vector(4096, 0, 1.0f));

        SpringAiVectorStoreRecallService service = new SpringAiVectorStoreRecallService(
                jdbcTemplate, embeddingModel, "default", 0, "", 8
        );
        var result = service.recall("测试问题", 2, QaFilters.empty(), "conv-2");

        assertEquals(2, result.size());
        assertEquals(2001L, result.getFirst().chunkId());
        assertEquals("法规X", result.getFirst().lawName());
    }

    private void insert4096(String id, String lawName, String vectorLiteral) {
        Map<String, Object> metadata = Map.of(
                "chunkId", Long.parseLong(id),
                "lawName", lawName,
                "articleNo", "第1条",
                "content", lawName + "内容",
                "versionNo", "v1"
        );
        String json = "{\"chunkId\":" + metadata.get("chunkId")
                + ",\"lawName\":\"" + metadata.get("lawName")
                + "\",\"articleNo\":\"第1条\",\"content\":\"" + metadata.get("content")
                + "\",\"versionNo\":\"v1\"}";
        jdbcTemplate.update(
                """
                insert into indexing.rag_chunk_vec_qwen3_4096_v1(id, content, metadata, embedding)
                values (?, ?, cast(? as jsonb), cast(? as halfvec(4096)))
                """,
                id,
                lawName + "内容",
                json,
                vectorLiteral
        );
    }

    private float[] vector(int dimension, int hotspot, float value) {
        float[] vector = new float[dimension];
        vector[hotspot] = value;
        return vector;
    }

    private String literal(int dimension, int hotspot, float value) {
        StringBuilder sb = new StringBuilder(dimension * 4);
        sb.append('[');
        for (int i = 0; i < dimension; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i == hotspot ? value : 0.0f);
        }
        sb.append(']');
        return sb.toString();
    }
}
