package my.inspectorrag.records.integration;

import my.inspectorrag.records.infrastructure.persistence.repository.MybatisRecordsRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = RecordsReplayIntegrationTest.TestApp.class)
class RecordsReplayIntegrationTest {

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
    private MybatisRecordsRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("create schema if not exists retrieval");
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
    }

    @Test
    void replayShouldReturnRewrittenQuestionAndRewriteQueriesFromRealDb() {
        long qaId = 920000000000000001L;
        jdbcTemplate.update(
                """
                insert into retrieval.qa_record
                (id, conversation_id, turn_no, question, normalized_question, rewritten_question, answer, answer_status, elapsed_ms, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'success', ?, now(), now())
                """,
                qaId,
                930000000000000001L,
                1,
                "原问题",
                "规范化问题",
                "改写主问题",
                "回答",
                88
        );
        jdbcTemplate.update(
                """
                insert into retrieval.qa_retrieval_snapshot
                (id, qa_id, filters_json, query_embedding_model, topk_requested, topn_returned, keyword_query, effective_query, rewrite_queries_json, created_at, updated_at)
                values (?, ?, '{}'::jsonb, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                """,
                920000000000000002L,
                qaId,
                "openai/text-embedding-3-small",
                20,
                2,
                "关键词",
                "改写主问题",
                "[\"改写主问题\",\"候选查询2\"]"
        );
        jdbcTemplate.update(
                """
                insert into retrieval.qa_candidate
                (id, qa_id, chunk_id, source_type, raw_score, rerank_score, final_score, rank_no, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                920000000000000003L,
                qaId,
                1001L,
                "hybrid",
                0.88,
                0.89,
                0.91,
                1
        );
        jdbcTemplate.update(
                """
                insert into retrieval.qa_evidence
                (id, qa_id, chunk_id, cite_no, quoted_text, used_in_answer, law_name, article_no, page_start, page_end, file_version, created_at, updated_at)
                values (?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?, now(), now())
                """,
                920000000000000004L,
                qaId,
                1001L,
                1,
                "引用内容",
                "法规A",
                "第1条",
                1,
                1,
                "v1"
        );

        var replay = repository.replay(qaId);
        assertTrue(replay.isPresent());
        assertEquals(930000000000000001L, replay.get().conversationId());
        assertEquals(1, replay.get().turnNo());
        assertEquals("改写主问题", replay.get().rewrittenQuestion());
        assertEquals(2, replay.get().rewriteQueries().size());
        assertEquals("候选查询2", replay.get().rewriteQueries().get(1));
        assertEquals(1, replay.get().candidates().size());
        assertEquals(1, replay.get().evidences().size());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("my.inspectorrag.records.infrastructure.persistence.mapper")
    @Import(MybatisRecordsRepository.class)
    static class TestApp {
    }
}
