package my.inspectorrag.searchandreturn.integration;

import my.inspectorrag.searchandreturn.infrastructure.persistence.repository.MybatisQaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = SearchAndReturnPersistenceIntegrationTest.TestApp.class,
        properties = {
                "spring.ai.openai.base-url=http://localhost:18080",
                "spring.ai.openai.api-key=test-key"
        }
)
class SearchAndReturnPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("inspector_rag_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MybatisQaRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("create schema if not exists retrieval");
        jdbcTemplate.execute("""
                create table if not exists retrieval.qa_conversation (
                    id bigint primary key,
                    created_at timestamptz not null,
                    updated_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists retrieval.qa_record (
                    id bigint primary key,
                    conversation_id bigint,
                    turn_no int,
                    question text not null,
                    normalized_question text not null,
                    rewritten_question text,
                    answer text,
                    answer_status varchar(32) not null,
                    reject_reason text,
                    elapsed_ms int,
                    created_at timestamptz not null,
                    updated_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists retrieval.qa_retrieval_snapshot (
                    id bigint primary key,
                    qa_id bigint not null,
                    filters_json jsonb,
                    query_embedding_model varchar(128),
                    query_embedding_profile varchar(64),
                    topk_requested int,
                    topn_returned int,
                    keyword_query text,
                    effective_query text,
                    rewrite_queries_json jsonb,
                    created_at timestamptz not null,
                    updated_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists retrieval.qa_candidate (
                    id bigint primary key,
                    qa_id bigint not null,
                    chunk_id bigint not null,
                    source_type varchar(16),
                    raw_score numeric(10,6),
                    rerank_score numeric(10,6),
                    final_score numeric(10,6),
                    rank_no int,
                    created_at timestamptz not null,
                    updated_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists retrieval.qa_evidence (
                    id bigint primary key,
                    qa_id bigint not null,
                    chunk_id bigint not null,
                    cite_no int,
                    quoted_text text,
                    used_in_answer boolean,
                    law_name varchar(512),
                    article_no varchar(64),
                    page_start int,
                    page_end int,
                    file_version varchar(64),
                    created_at timestamptz not null,
                    updated_at timestamptz not null
                )
                """);
        jdbcTemplate.execute("truncate table retrieval.qa_evidence");
        jdbcTemplate.execute("truncate table retrieval.qa_candidate");
        jdbcTemplate.execute("truncate table retrieval.qa_retrieval_snapshot");
        jdbcTemplate.execute("truncate table retrieval.qa_record");
        jdbcTemplate.execute("truncate table retrieval.qa_conversation");
    }

    @Test
    void shouldPersistAndLoadRewrittenQuestionAndRewriteQueriesFromRealDb() {
        OffsetDateTime now = OffsetDateTime.now();
        long qaId = 910000000000000001L;
        long conversationId = 810000000000000001L;
        repository.insertConversation(conversationId, now);
        repository.insertQaRecord(
                qaId,
                conversationId,
                1,
                "原始问题",
                "规范化问题",
                "改写主问题",
                "回答内容",
                120,
                now
        );
        repository.insertRetrievalSnapshot(
                910000000000000002L,
                qaId,
                "openai/text-embedding-3-small",
                "qwen3_2048_v1",
                20,
                3,
                "{}",
                "关键词",
                "改写主问题",
                "[\"改写主问题\",\"候选查询2\"]",
                now
        );

        var detail = repository.findQaDetail(qaId);
        assertTrue(detail.isPresent());
        assertEquals("改写主问题", detail.get().rewrittenQuestion());
        assertEquals(2, detail.get().rewriteQueries().size());
        assertEquals("候选查询2", detail.get().rewriteQueries().get(1));

        String persistedRewritten = jdbcTemplate.queryForObject(
                "select rewritten_question from retrieval.qa_record where id = ?",
                String.class,
                qaId
        );
        assertEquals("改写主问题", persistedRewritten);

        String persistedQueries = jdbcTemplate.queryForObject(
                "select rewrite_queries_json::text from retrieval.qa_retrieval_snapshot where qa_id = ?",
                String.class,
                qaId
        );
        assertTrue(persistedQueries.contains("候选查询2"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("my.inspectorrag.searchandreturn.infrastructure.persistence.mapper")
    @Import(MybatisQaRepository.class)
    static class TestApp {
    }
}
