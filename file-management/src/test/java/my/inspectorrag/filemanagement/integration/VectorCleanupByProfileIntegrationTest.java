package my.inspectorrag.filemanagement.integration;

import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentCommandMapper;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentQueryMapper;
import my.inspectorrag.filemanagement.infrastructure.persistence.repository.MybatisDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class VectorCleanupByProfileIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("inspector_rag")
            .withUsername("postgres")
            .withPassword("postgres");

    private JdbcTemplate jdbcTemplate;
    private MybatisDocumentRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("create schema if not exists ingest");
        jdbcTemplate.execute("create schema if not exists indexing");
        jdbcTemplate.execute("drop table if exists ingest.law_chunk");
        jdbcTemplate.execute("drop table if exists indexing.rag_chunk_vec_qwen3_2048_v1");
        jdbcTemplate.execute("drop table if exists indexing.rag_chunk_vec_qwen3_1024_v1");
        jdbcTemplate.execute("drop table if exists indexing.embedding_profile");
        jdbcTemplate.execute("""
                create table ingest.law_chunk (
                    id bigint primary key,
                    doc_id bigint not null
                )
                """);
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
                    embedding halfvec(2048)
                )
                """);
        jdbcTemplate.execute("""
                create table indexing.rag_chunk_vec_qwen3_1024_v1 (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding halfvec(1024)
                )
                """);

        DocumentCommandMapper commandMapper = Mockito.mock(DocumentCommandMapper.class);
        DocumentQueryMapper queryMapper = Mockito.mock(DocumentQueryMapper.class);
        repository = new MybatisDocumentRepository(commandMapper, queryMapper, jdbcTemplate);
    }

    @Test
    void deleteVectorsByDocIdShouldDeleteFromAllProfileTables() {
        jdbcTemplate.update("insert into ingest.law_chunk(id, doc_id) values (1001, 5001), (1002, 5001), (2001, 6001)");
        jdbcTemplate.update("""
                insert into indexing.embedding_profile
                (id, profile_key, provider, model_name, dimension, vector_type, distance_metric, storage_table, read_enabled, write_enabled, is_default_read)
                values
                (1, 'qwen3_2048_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 2048, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_2048_v1', true, true, true),
                (2, 'qwen3_1024_v1', 'openai-compatible', 'qwen/qwen3-embedding-8b', 1024, 'halfvec', 'cosine', 'indexing.rag_chunk_vec_qwen3_1024_v1', true, true, false)
                """);
        insert2048("1001");
        insert2048("1002");
        insert2048("2001");
        insert1024("1001");
        insert1024("1002");
        insert1024("2001");

        repository.deleteVectorsByDocId(5001L);

        Integer remain2048 = jdbcTemplate.queryForObject("select count(*) from indexing.rag_chunk_vec_qwen3_2048_v1", Integer.class);
        Integer remain1024 = jdbcTemplate.queryForObject("select count(*) from indexing.rag_chunk_vec_qwen3_1024_v1", Integer.class);
        assertEquals(1, remain2048);
        assertEquals(1, remain1024);
    }

    private void insert2048(String id) {
        jdbcTemplate.update(
                "insert into indexing.rag_chunk_vec_qwen3_2048_v1(id, embedding) values (?, cast(? as halfvec(2048)))",
                id,
                zeroLiteral(2048)
        );
    }

    private void insert1024(String id) {
        jdbcTemplate.update(
                "insert into indexing.rag_chunk_vec_qwen3_1024_v1(id, embedding) values (?, cast(? as halfvec(1024)))",
                id,
                zeroLiteral(1024)
        );
    }

    private String zeroLiteral(int dimension) {
        StringBuilder sb = new StringBuilder(dimension * 2 + 2);
        sb.append('[');
        for (int i = 0; i < dimension; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('0');
        }
        sb.append(']');
        return sb.toString();
    }
}
