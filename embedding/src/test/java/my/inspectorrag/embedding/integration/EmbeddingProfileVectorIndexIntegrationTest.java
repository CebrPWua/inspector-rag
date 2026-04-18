package my.inspectorrag.embedding.integration;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.model.value.ChunkId;
import my.inspectorrag.embedding.infrastructure.gateway.SpringAiPgVectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
class EmbeddingProfileVectorIndexIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("inspector_rag")
            .withUsername("postgres")
            .withPassword("postgres");

    private JdbcTemplate jdbcTemplate;
    private SpringAiPgVectorIndexService vectorIndexService;

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
        jdbcTemplate.execute("drop table if exists indexing.rag_chunk_vec_qwen3_1024_v1");
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
        jdbcTemplate.execute("""
                create table indexing.rag_chunk_vec_qwen3_2048_v1 (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding halfvec(2048),
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.execute("""
                create table indexing.rag_chunk_vec_qwen3_1024_v1 (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding halfvec(1024),
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.update("""
                insert into indexing.embedding_profile
                (id, profile_key, provider, model_name, dimension, vector_type, distance_metric, storage_table, read_enabled, write_enabled, is_default_read)
                values
                (1, 'qwen3_2048_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 2048, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_2048_v1', true, true, true),
                (2, 'qwen3_1024_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 1024, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_1024_v1', true, true, false)
                """);

        EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            float[] vector = new float[4096];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = i;
            }
            return vector;
        });
        vectorIndexService = new SpringAiPgVectorIndexService(jdbcTemplate, embeddingModel);
    }

    @Test
    void upsertShouldProjectToProfileDimensionsAndWriteAllEnabledProfiles() {
        PendingChunk chunk = new PendingChunk(
                ChunkId.of(1001L),
                "法规A",
                "章节A",
                "小节A",
                "第1条",
                "测试内容",
                1,
                2,
                "v1",
                "active"
        );

        vectorIndexService.upsert(chunk);

        Integer dims2048 = jdbcTemplate.queryForObject(
                "select vector_dims(embedding::vector) from indexing.rag_chunk_vec_qwen3_2048_v1 where id = '1001'",
                Integer.class
        );
        Integer dims1024 = jdbcTemplate.queryForObject(
                "select vector_dims(embedding::vector) from indexing.rag_chunk_vec_qwen3_1024_v1 where id = '1001'",
                Integer.class
        );

        assertEquals(2048, dims2048);
        assertEquals(1024, dims1024);
    }
}
