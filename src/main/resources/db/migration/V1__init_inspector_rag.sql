-- Inspector RAG database baseline
-- Target: PostgreSQL 17
-- Notes:
-- 1) Please create database `inspector_rag` separately if needed.
-- 2) All timestamps use timestamptz, application displays in Asia/Shanghai.

-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Schemas
CREATE SCHEMA IF NOT EXISTS ingest;
CREATE SCHEMA IF NOT EXISTS indexing;
CREATE SCHEMA IF NOT EXISTS retrieval;
CREATE SCHEMA IF NOT EXISTS ops;

-- Shared trigger helper
CREATE OR REPLACE FUNCTION ops.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- =========================
-- ingest
-- =========================
CREATE TABLE IF NOT EXISTS ingest.source_document (
    id               BIGINT PRIMARY KEY,
    law_name         VARCHAR(512) NOT NULL,
    law_code         VARCHAR(128) NOT NULL,
    doc_type         VARCHAR(64)  NOT NULL,
    source_file_name VARCHAR(512) NOT NULL,
    file_hash        CHAR(64)     NOT NULL,
    publish_org      VARCHAR(255),
    publish_date     DATE,
    effective_date   DATE,
    expired_date     DATE,
    version_no       VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'active',
    parse_status     VARCHAR(32)  NOT NULL DEFAULT 'pending',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_source_document_file_hash UNIQUE (file_hash),
    CONSTRAINT uk_source_document_law_version UNIQUE (law_code, version_no),
    CONSTRAINT ck_source_document_status CHECK (status IN ('active', 'inactive', 'pending_confirm')),
    CONSTRAINT ck_source_document_parse_status CHECK (parse_status IN ('pending', 'processing', 'success', 'failed')),
    CONSTRAINT ck_source_document_hash_hex CHECK (file_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_source_document_dates CHECK (expired_date IS NULL OR effective_date IS NULL OR expired_date >= effective_date)
);

CREATE INDEX IF NOT EXISTS idx_source_document_status ON ingest.source_document (status);
CREATE INDEX IF NOT EXISTS idx_source_document_law_code ON ingest.source_document (law_code);
CREATE INDEX IF NOT EXISTS idx_source_document_effective_date ON ingest.source_document (effective_date);

CREATE TABLE IF NOT EXISTS ingest.document_file (
    id              BIGINT PRIMARY KEY,
    doc_id          BIGINT       NOT NULL,
    storage_path    TEXT         NOT NULL,
    mime_type       VARCHAR(127) NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    upload_batch_no VARCHAR(64),
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_document_file_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document (id) ON DELETE CASCADE,
    CONSTRAINT uk_document_file_doc_sha UNIQUE (doc_id, sha256),
    CONSTRAINT ck_document_file_sha_hex CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_file_size CHECK (file_size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_document_file_doc_id ON ingest.document_file (doc_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_document_file_primary_per_doc ON ingest.document_file (doc_id) WHERE is_primary;

CREATE TABLE IF NOT EXISTS ingest.law_chunk (
    id               BIGINT PRIMARY KEY,
    doc_id           BIGINT       NOT NULL,
    chapter_title    VARCHAR(255),
    section_title    VARCHAR(255),
    article_no       VARCHAR(64)  NOT NULL,
    item_no          VARCHAR(64)  NOT NULL DEFAULT '',
    content          TEXT         NOT NULL,
    page_start       INT,
    page_end         INT,
    chunk_seq        INT          NOT NULL DEFAULT 1,
    content_hash     CHAR(64)     NOT NULL,
    embedding_status VARCHAR(32)  NOT NULL DEFAULT 'pending',
    status           VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_law_chunk_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document (id) ON DELETE CASCADE,
    CONSTRAINT uk_law_chunk_loc UNIQUE (doc_id, article_no, item_no, chunk_seq),
    CONSTRAINT ck_law_chunk_content_hash_hex CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_law_chunk_embedding_status CHECK (embedding_status IN ('pending', 'processing', 'success', 'failed', 'skipped')),
    CONSTRAINT ck_law_chunk_status CHECK (status IN ('active', 'inactive')),
    CONSTRAINT ck_law_chunk_chunk_seq CHECK (chunk_seq > 0),
    CONSTRAINT ck_law_chunk_page_range CHECK (
        (page_start IS NULL AND page_end IS NULL)
        OR (page_start IS NOT NULL AND page_end IS NOT NULL AND page_start > 0 AND page_end >= page_start)
    )
);

CREATE INDEX IF NOT EXISTS idx_law_chunk_doc_status ON ingest.law_chunk (doc_id, status);
CREATE INDEX IF NOT EXISTS idx_law_chunk_embedding_status ON ingest.law_chunk (embedding_status, updated_at);
CREATE INDEX IF NOT EXISTS idx_law_chunk_content_hash ON ingest.law_chunk (content_hash);

CREATE TABLE IF NOT EXISTS ingest.chunk_tag (
    id         BIGINT PRIMARY KEY,
    chunk_id   BIGINT       NOT NULL,
    tag_type   VARCHAR(64)  NOT NULL,
    tag_value  VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chunk_tag_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk (id) ON DELETE CASCADE,
    CONSTRAINT uk_chunk_tag UNIQUE (chunk_id, tag_type, tag_value)
);

CREATE INDEX IF NOT EXISTS idx_chunk_tag_type_value ON ingest.chunk_tag (tag_type, tag_value);

-- =========================
-- indexing
-- =========================
CREATE TABLE IF NOT EXISTS indexing.embedding_model (
    id         BIGINT PRIMARY KEY,
    model_name VARCHAR(128) NOT NULL,
    dimension  INT          NOT NULL,
    version    VARCHAR(64)  NOT NULL,
    provider   VARCHAR(64)  NOT NULL DEFAULT 'oneapi',
    is_active  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_embedding_model UNIQUE (model_name, version),
    CONSTRAINT ck_embedding_model_dimension CHECK (dimension > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_embedding_model_active_one_per_name
    ON indexing.embedding_model (model_name)
    WHERE is_active;

CREATE TABLE IF NOT EXISTS indexing.law_chunk_embedding (
    id                BIGINT PRIMARY KEY,
    chunk_id          BIGINT       NOT NULL,
    model_id          BIGINT       NOT NULL,
    embedding_version VARCHAR(64)  NOT NULL,
    embedding         VECTOR(1536) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_law_chunk_embedding_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk (id) ON DELETE CASCADE,
    CONSTRAINT fk_law_chunk_embedding_model FOREIGN KEY (model_id) REFERENCES indexing.embedding_model (id),
    CONSTRAINT uk_law_chunk_embedding UNIQUE (chunk_id, model_id, embedding_version)
);

CREATE INDEX IF NOT EXISTS idx_law_chunk_embedding_model_id ON indexing.law_chunk_embedding (model_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_law_chunk_embedding_chunk_id ON indexing.law_chunk_embedding (chunk_id);
CREATE INDEX IF NOT EXISTS idx_law_chunk_embedding_vector_cosine
    ON indexing.law_chunk_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- =========================
-- retrieval
-- =========================
CREATE TABLE IF NOT EXISTS retrieval.qa_record (
    id                  BIGINT PRIMARY KEY,
    question            TEXT        NOT NULL,
    normalized_question TEXT        NOT NULL,
    answer              TEXT,
    answer_status       VARCHAR(32) NOT NULL,
    reject_reason       TEXT,
    elapsed_ms          INT,
    user_feedback       VARCHAR(16) NOT NULL DEFAULT 'unrated',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_qa_record_status CHECK (answer_status IN ('success', 'reject', 'failed')),
    CONSTRAINT ck_qa_record_feedback CHECK (user_feedback IN ('useful', 'useless', 'unrated')),
    CONSTRAINT ck_qa_record_elapsed CHECK (elapsed_ms IS NULL OR elapsed_ms >= 0),
    CONSTRAINT ck_qa_record_reject_reason CHECK (
        answer_status <> 'reject'
        OR (reject_reason IS NOT NULL AND LENGTH(BTRIM(reject_reason)) > 0)
    ),
    CONSTRAINT ck_qa_record_answer_for_success CHECK (
        answer_status <> 'success'
        OR (answer IS NOT NULL AND LENGTH(BTRIM(answer)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_qa_record_created_at_desc ON retrieval.qa_record (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_record_status_created_at ON retrieval.qa_record (answer_status, created_at DESC);

CREATE TABLE IF NOT EXISTS retrieval.qa_retrieval_snapshot (
    id                    BIGINT PRIMARY KEY,
    qa_id                 BIGINT       NOT NULL,
    filters_json          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    query_embedding_model VARCHAR(128) NOT NULL,
    topk_requested        INT          NOT NULL,
    topn_returned         INT,
    keyword_query         TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_qa_snapshot_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record (id) ON DELETE CASCADE,
    CONSTRAINT uk_qa_snapshot_one_per_qa UNIQUE (qa_id),
    CONSTRAINT ck_qa_snapshot_topk CHECK (topk_requested > 0),
    CONSTRAINT ck_qa_snapshot_topn CHECK (topn_returned IS NULL OR topn_returned >= 0)
);

CREATE INDEX IF NOT EXISTS idx_qa_snapshot_filters_gin ON retrieval.qa_retrieval_snapshot USING gin (filters_json);

CREATE TABLE IF NOT EXISTS retrieval.qa_candidate (
    id           BIGINT PRIMARY KEY,
    qa_id        BIGINT       NOT NULL,
    chunk_id     BIGINT       NOT NULL,
    source_type  VARCHAR(16)  NOT NULL,
    raw_score    NUMERIC(10, 6),
    rerank_score NUMERIC(10, 6),
    final_score  NUMERIC(10, 6),
    rank_no      INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_qa_candidate_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_candidate_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk (id),
    CONSTRAINT ck_qa_candidate_source_type CHECK (source_type IN ('vector', 'keyword', 'hybrid')),
    CONSTRAINT ck_qa_candidate_rank_no CHECK (rank_no > 0),
    CONSTRAINT ck_qa_candidate_score_exists CHECK (
        raw_score IS NOT NULL OR rerank_score IS NOT NULL OR final_score IS NOT NULL
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_qa_candidate_qa_rank ON retrieval.qa_candidate (qa_id, rank_no);
CREATE INDEX IF NOT EXISTS idx_qa_candidate_qa_rank ON retrieval.qa_candidate (qa_id, rank_no);
CREATE INDEX IF NOT EXISTS idx_qa_candidate_chunk_id ON retrieval.qa_candidate (chunk_id);

CREATE TABLE IF NOT EXISTS retrieval.qa_evidence (
    id             BIGINT PRIMARY KEY,
    qa_id          BIGINT       NOT NULL,
    chunk_id       BIGINT       NOT NULL,
    cite_no        INT          NOT NULL,
    quoted_text    TEXT         NOT NULL,
    used_in_answer BOOLEAN      NOT NULL DEFAULT TRUE,
    law_name       VARCHAR(512),
    article_no     VARCHAR(64),
    page_start     INT,
    page_end       INT,
    file_version   VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_qa_evidence_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_evidence_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk (id),
    CONSTRAINT uk_qa_evidence_qa_cite UNIQUE (qa_id, cite_no),
    CONSTRAINT ck_qa_evidence_cite_no CHECK (cite_no > 0),
    CONSTRAINT ck_qa_evidence_quoted_text CHECK (LENGTH(BTRIM(quoted_text)) > 0),
    CONSTRAINT ck_qa_evidence_page_range CHECK (
        (page_start IS NULL AND page_end IS NULL)
        OR (page_start IS NOT NULL AND page_end IS NOT NULL AND page_start > 0 AND page_end >= page_start)
    )
);

CREATE INDEX IF NOT EXISTS idx_qa_evidence_qa_cite ON retrieval.qa_evidence (qa_id, cite_no);
CREATE INDEX IF NOT EXISTS idx_qa_evidence_chunk_id ON retrieval.qa_evidence (chunk_id);

-- =========================
-- ops
-- =========================
CREATE TABLE IF NOT EXISTS ops.import_task (
    id          BIGINT PRIMARY KEY,
    doc_id      BIGINT       NOT NULL,
    task_type   VARCHAR(32)  NOT NULL,
    task_status VARCHAR(32)  NOT NULL DEFAULT 'pending',
    retry_count INT          NOT NULL DEFAULT 0,
    max_retry   INT          NOT NULL DEFAULT 3,
    error_msg   TEXT,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_import_task_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document (id) ON DELETE CASCADE,
    CONSTRAINT ck_import_task_type CHECK (task_type IN ('parse', 'chunk', 'embed', 'reindex', 'cleanup')),
    CONSTRAINT ck_import_task_status CHECK (task_status IN ('pending', 'processing', 'success', 'failed', 'retrying')),
    CONSTRAINT ck_import_task_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_import_task_max_retry CHECK (max_retry > 0),
    CONSTRAINT ck_import_task_retry_bound CHECK (retry_count <= max_retry),
    CONSTRAINT ck_import_task_time_range CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);

CREATE INDEX IF NOT EXISTS idx_import_task_status_created_at ON ops.import_task (task_status, created_at);
CREATE INDEX IF NOT EXISTS idx_import_task_doc_type_created_at ON ops.import_task (doc_id, task_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_import_task_claimable ON ops.import_task (created_at, id)
    WHERE task_status IN ('pending', 'retrying');

CREATE TABLE IF NOT EXISTS ops.task_retry_log (
    id          BIGINT PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    attempt_no  INT          NOT NULL,
    error_code  VARCHAR(64),
    error_msg   TEXT         NOT NULL,
    payload_json JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_retry_log_task FOREIGN KEY (task_id) REFERENCES ops.import_task (id) ON DELETE CASCADE,
    CONSTRAINT uk_task_retry_log_attempt UNIQUE (task_id, attempt_no),
    CONSTRAINT ck_task_retry_log_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_task_retry_log_error_msg CHECK (LENGTH(BTRIM(error_msg)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_task_retry_log_payload_gin ON ops.task_retry_log USING gin (payload_json);
CREATE INDEX IF NOT EXISTS idx_task_retry_log_task_id ON ops.task_retry_log (task_id);

CREATE TABLE IF NOT EXISTS ops.dead_letter_task (
    id                BIGINT PRIMARY KEY,
    task_id           BIGINT       NOT NULL,
    doc_id            BIGINT,
    task_type         VARCHAR(32)  NOT NULL,
    last_error_msg    TEXT         NOT NULL,
    payload_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    assigned_to       VARCHAR(64),
    resolution_status VARCHAR(32)  NOT NULL DEFAULT 'open',
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_dead_letter_task_task UNIQUE (task_id),
    CONSTRAINT fk_dead_letter_task_task FOREIGN KEY (task_id) REFERENCES ops.import_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_dead_letter_task_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document (id) ON DELETE SET NULL,
    CONSTRAINT ck_dead_letter_task_type CHECK (task_type IN ('parse', 'chunk', 'embed', 'reindex', 'cleanup')),
    CONSTRAINT ck_dead_letter_task_last_error CHECK (LENGTH(BTRIM(last_error_msg)) > 0),
    CONSTRAINT ck_dead_letter_task_resolution_status CHECK (resolution_status IN ('open', 'processing', 'resolved', 'closed')),
    CONSTRAINT ck_dead_letter_task_resolved_at CHECK (
        resolved_at IS NULL OR resolution_status IN ('resolved', 'closed')
    )
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_status_created_at ON ops.dead_letter_task (resolution_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dead_letter_payload_gin ON ops.dead_letter_task USING gin (payload_json);

-- Concurrent-safe task claim API (FOR UPDATE SKIP LOCKED)
CREATE OR REPLACE FUNCTION ops.claim_import_tasks(p_limit INT DEFAULT 10)
RETURNS SETOF ops.import_task
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH picked AS (
        SELECT t.id
        FROM ops.import_task t
        WHERE t.task_status IN ('pending', 'retrying')
          AND t.retry_count <= t.max_retry
        ORDER BY t.created_at, t.id
        FOR UPDATE SKIP LOCKED
        LIMIT p_limit
    )
    UPDATE ops.import_task t
       SET task_status = 'processing',
           started_at = COALESCE(t.started_at, NOW()),
           updated_at = NOW()
      FROM picked p
     WHERE t.id = p.id
    RETURNING t.*;
END;
$$;

-- Updated-at triggers
CREATE TRIGGER trg_source_document_updated_at
    BEFORE UPDATE ON ingest.source_document
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_document_file_updated_at
    BEFORE UPDATE ON ingest.document_file
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_law_chunk_updated_at
    BEFORE UPDATE ON ingest.law_chunk
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_chunk_tag_updated_at
    BEFORE UPDATE ON ingest.chunk_tag
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_embedding_model_updated_at
    BEFORE UPDATE ON indexing.embedding_model
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_law_chunk_embedding_updated_at
    BEFORE UPDATE ON indexing.law_chunk_embedding
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_qa_record_updated_at
    BEFORE UPDATE ON retrieval.qa_record
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_qa_retrieval_snapshot_updated_at
    BEFORE UPDATE ON retrieval.qa_retrieval_snapshot
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_qa_candidate_updated_at
    BEFORE UPDATE ON retrieval.qa_candidate
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_qa_evidence_updated_at
    BEFORE UPDATE ON retrieval.qa_evidence
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_import_task_updated_at
    BEFORE UPDATE ON ops.import_task
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_task_retry_log_updated_at
    BEFORE UPDATE ON ops.task_retry_log
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

CREATE TRIGGER trg_dead_letter_task_updated_at
    BEFORE UPDATE ON ops.dead_letter_task
    FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();

-- =========================
-- Comments (zh-CN)
-- =========================

-- ingest.source_document
COMMENT ON TABLE ingest.source_document IS '法规主档表';
COMMENT ON COLUMN ingest.source_document.id IS '主键ID';
COMMENT ON COLUMN ingest.source_document.law_name IS '法规名称';
COMMENT ON COLUMN ingest.source_document.law_code IS '法规编码/标准号';
COMMENT ON COLUMN ingest.source_document.doc_type IS '文档类型（法律/条例/标准/通知等）';
COMMENT ON COLUMN ingest.source_document.source_file_name IS '原始文件名';
COMMENT ON COLUMN ingest.source_document.file_hash IS '文件SHA-256哈希';
COMMENT ON COLUMN ingest.source_document.publish_org IS '发布单位';
COMMENT ON COLUMN ingest.source_document.publish_date IS '发布日期';
COMMENT ON COLUMN ingest.source_document.effective_date IS '生效日期';
COMMENT ON COLUMN ingest.source_document.expired_date IS '失效日期';
COMMENT ON COLUMN ingest.source_document.version_no IS '版本号';
COMMENT ON COLUMN ingest.source_document.status IS '法规状态（active/inactive/pending_confirm）';
COMMENT ON COLUMN ingest.source_document.parse_status IS '解析状态（pending/processing/success/failed）';
COMMENT ON COLUMN ingest.source_document.created_at IS '创建时间';
COMMENT ON COLUMN ingest.source_document.updated_at IS '更新时间';

-- ingest.document_file
COMMENT ON TABLE ingest.document_file IS '法规物理文件表';
COMMENT ON COLUMN ingest.document_file.id IS '主键ID';
COMMENT ON COLUMN ingest.document_file.doc_id IS '关联法规主档ID';
COMMENT ON COLUMN ingest.document_file.storage_path IS '对象存储路径';
COMMENT ON COLUMN ingest.document_file.mime_type IS 'MIME类型';
COMMENT ON COLUMN ingest.document_file.file_size_bytes IS '文件大小（字节）';
COMMENT ON COLUMN ingest.document_file.sha256 IS '文件SHA-256哈希';
COMMENT ON COLUMN ingest.document_file.upload_batch_no IS '上传批次号';
COMMENT ON COLUMN ingest.document_file.is_primary IS '是否主文件';
COMMENT ON COLUMN ingest.document_file.created_at IS '创建时间';
COMMENT ON COLUMN ingest.document_file.updated_at IS '更新时间';

-- ingest.law_chunk
COMMENT ON TABLE ingest.law_chunk IS '法规条款切分表';
COMMENT ON COLUMN ingest.law_chunk.id IS '主键ID';
COMMENT ON COLUMN ingest.law_chunk.doc_id IS '关联法规主档ID';
COMMENT ON COLUMN ingest.law_chunk.chapter_title IS '章标题';
COMMENT ON COLUMN ingest.law_chunk.section_title IS '节标题';
COMMENT ON COLUMN ingest.law_chunk.article_no IS '条号';
COMMENT ON COLUMN ingest.law_chunk.item_no IS '款/项号';
COMMENT ON COLUMN ingest.law_chunk.content IS '条款正文';
COMMENT ON COLUMN ingest.law_chunk.page_start IS '起始页码';
COMMENT ON COLUMN ingest.law_chunk.page_end IS '结束页码';
COMMENT ON COLUMN ingest.law_chunk.chunk_seq IS '切分顺序号';
COMMENT ON COLUMN ingest.law_chunk.content_hash IS '内容SHA-256哈希';
COMMENT ON COLUMN ingest.law_chunk.embedding_status IS '向量化状态（pending/processing/success/failed/skipped）';
COMMENT ON COLUMN ingest.law_chunk.status IS '条款状态（active/inactive）';
COMMENT ON COLUMN ingest.law_chunk.created_at IS '创建时间';
COMMENT ON COLUMN ingest.law_chunk.updated_at IS '更新时间';

-- ingest.chunk_tag
COMMENT ON TABLE ingest.chunk_tag IS '条款标签表';
COMMENT ON COLUMN ingest.chunk_tag.id IS '主键ID';
COMMENT ON COLUMN ingest.chunk_tag.chunk_id IS '关联条款ID';
COMMENT ON COLUMN ingest.chunk_tag.tag_type IS '标签类型';
COMMENT ON COLUMN ingest.chunk_tag.tag_value IS '标签值';
COMMENT ON COLUMN ingest.chunk_tag.created_at IS '创建时间';
COMMENT ON COLUMN ingest.chunk_tag.updated_at IS '更新时间';

-- indexing.embedding_model
COMMENT ON TABLE indexing.embedding_model IS '向量模型元数据表';
COMMENT ON COLUMN indexing.embedding_model.id IS '主键ID';
COMMENT ON COLUMN indexing.embedding_model.model_name IS '模型名称';
COMMENT ON COLUMN indexing.embedding_model.dimension IS '向量维度';
COMMENT ON COLUMN indexing.embedding_model.version IS '模型版本';
COMMENT ON COLUMN indexing.embedding_model.provider IS '模型服务提供方';
COMMENT ON COLUMN indexing.embedding_model.is_active IS '是否当前激活版本';
COMMENT ON COLUMN indexing.embedding_model.created_at IS '创建时间';
COMMENT ON COLUMN indexing.embedding_model.updated_at IS '更新时间';

-- indexing.law_chunk_embedding
COMMENT ON TABLE indexing.law_chunk_embedding IS '条款向量表';
COMMENT ON COLUMN indexing.law_chunk_embedding.id IS '主键ID';
COMMENT ON COLUMN indexing.law_chunk_embedding.chunk_id IS '关联条款ID';
COMMENT ON COLUMN indexing.law_chunk_embedding.model_id IS '关联向量模型ID';
COMMENT ON COLUMN indexing.law_chunk_embedding.embedding_version IS '向量数据版本';
COMMENT ON COLUMN indexing.law_chunk_embedding.embedding IS '向量字段';
COMMENT ON COLUMN indexing.law_chunk_embedding.created_at IS '创建时间';
COMMENT ON COLUMN indexing.law_chunk_embedding.updated_at IS '更新时间';

-- retrieval.qa_record
COMMENT ON TABLE retrieval.qa_record IS '问答记录主表';
COMMENT ON COLUMN retrieval.qa_record.id IS '主键ID';
COMMENT ON COLUMN retrieval.qa_record.question IS '用户原始问题';
COMMENT ON COLUMN retrieval.qa_record.normalized_question IS '标准化后的问题';
COMMENT ON COLUMN retrieval.qa_record.answer IS '最终回答';
COMMENT ON COLUMN retrieval.qa_record.answer_status IS '回答状态（success/reject/failed）';
COMMENT ON COLUMN retrieval.qa_record.reject_reason IS '拒答原因';
COMMENT ON COLUMN retrieval.qa_record.elapsed_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN retrieval.qa_record.user_feedback IS '用户反馈（useful/useless/unrated）';
COMMENT ON COLUMN retrieval.qa_record.created_at IS '创建时间';
COMMENT ON COLUMN retrieval.qa_record.updated_at IS '更新时间';

-- retrieval.qa_retrieval_snapshot
COMMENT ON TABLE retrieval.qa_retrieval_snapshot IS '检索快照表';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.id IS '主键ID';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.qa_id IS '关联问答ID';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.filters_json IS '检索过滤条件快照(JSON)';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.query_embedding_model IS '查询向量模型名称';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.topk_requested IS '请求召回TopK';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.topn_returned IS '最终返回TopN';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.keyword_query IS '关键词检索串';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.updated_at IS '更新时间';

-- retrieval.qa_candidate
COMMENT ON TABLE retrieval.qa_candidate IS '候选结果明细表';
COMMENT ON COLUMN retrieval.qa_candidate.id IS '主键ID';
COMMENT ON COLUMN retrieval.qa_candidate.qa_id IS '关联问答ID';
COMMENT ON COLUMN retrieval.qa_candidate.chunk_id IS '关联条款ID';
COMMENT ON COLUMN retrieval.qa_candidate.source_type IS '候选来源（vector/keyword/hybrid）';
COMMENT ON COLUMN retrieval.qa_candidate.raw_score IS '原始检索分';
COMMENT ON COLUMN retrieval.qa_candidate.rerank_score IS '重排分';
COMMENT ON COLUMN retrieval.qa_candidate.final_score IS '融合后最终分';
COMMENT ON COLUMN retrieval.qa_candidate.rank_no IS '候选排序名次';
COMMENT ON COLUMN retrieval.qa_candidate.created_at IS '创建时间';
COMMENT ON COLUMN retrieval.qa_candidate.updated_at IS '更新时间';

-- retrieval.qa_evidence
COMMENT ON TABLE retrieval.qa_evidence IS '最终证据明细表';
COMMENT ON COLUMN retrieval.qa_evidence.id IS '主键ID';
COMMENT ON COLUMN retrieval.qa_evidence.qa_id IS '关联问答ID';
COMMENT ON COLUMN retrieval.qa_evidence.chunk_id IS '关联条款ID';
COMMENT ON COLUMN retrieval.qa_evidence.cite_no IS '引用序号';
COMMENT ON COLUMN retrieval.qa_evidence.quoted_text IS '引用原文片段';
COMMENT ON COLUMN retrieval.qa_evidence.used_in_answer IS '是否用于最终回答';
COMMENT ON COLUMN retrieval.qa_evidence.law_name IS '法规名称快照';
COMMENT ON COLUMN retrieval.qa_evidence.article_no IS '条号快照';
COMMENT ON COLUMN retrieval.qa_evidence.page_start IS '起始页码快照';
COMMENT ON COLUMN retrieval.qa_evidence.page_end IS '结束页码快照';
COMMENT ON COLUMN retrieval.qa_evidence.file_version IS '文件版本快照';
COMMENT ON COLUMN retrieval.qa_evidence.created_at IS '创建时间';
COMMENT ON COLUMN retrieval.qa_evidence.updated_at IS '更新时间';

-- ops.import_task
COMMENT ON TABLE ops.import_task IS '导入与处理任务主表';
COMMENT ON COLUMN ops.import_task.id IS '主键ID';
COMMENT ON COLUMN ops.import_task.doc_id IS '关联法规主档ID';
COMMENT ON COLUMN ops.import_task.task_type IS '任务类型（parse/chunk/embed/reindex/cleanup）';
COMMENT ON COLUMN ops.import_task.task_status IS '任务状态（pending/processing/success/failed/retrying）';
COMMENT ON COLUMN ops.import_task.retry_count IS '当前重试次数';
COMMENT ON COLUMN ops.import_task.max_retry IS '最大重试次数';
COMMENT ON COLUMN ops.import_task.error_msg IS '错误信息';
COMMENT ON COLUMN ops.import_task.started_at IS '任务开始时间';
COMMENT ON COLUMN ops.import_task.finished_at IS '任务结束时间';
COMMENT ON COLUMN ops.import_task.created_at IS '创建时间';
COMMENT ON COLUMN ops.import_task.updated_at IS '更新时间';

-- ops.task_retry_log
COMMENT ON TABLE ops.task_retry_log IS '任务重试日志表';
COMMENT ON COLUMN ops.task_retry_log.id IS '主键ID';
COMMENT ON COLUMN ops.task_retry_log.task_id IS '关联任务ID';
COMMENT ON COLUMN ops.task_retry_log.attempt_no IS '第几次重试';
COMMENT ON COLUMN ops.task_retry_log.error_code IS '错误码';
COMMENT ON COLUMN ops.task_retry_log.error_msg IS '错误信息';
COMMENT ON COLUMN ops.task_retry_log.payload_json IS '重试上下文(JSON)';
COMMENT ON COLUMN ops.task_retry_log.created_at IS '创建时间';
COMMENT ON COLUMN ops.task_retry_log.updated_at IS '更新时间';

-- ops.dead_letter_task
COMMENT ON TABLE ops.dead_letter_task IS '死信任务表';
COMMENT ON COLUMN ops.dead_letter_task.id IS '主键ID';
COMMENT ON COLUMN ops.dead_letter_task.task_id IS '关联任务ID';
COMMENT ON COLUMN ops.dead_letter_task.doc_id IS '关联法规主档ID';
COMMENT ON COLUMN ops.dead_letter_task.task_type IS '任务类型（parse/chunk/embed/reindex/cleanup）';
COMMENT ON COLUMN ops.dead_letter_task.last_error_msg IS '最后一次错误信息';
COMMENT ON COLUMN ops.dead_letter_task.payload_json IS '死信上下文(JSON)';
COMMENT ON COLUMN ops.dead_letter_task.assigned_to IS '人工处理人';
COMMENT ON COLUMN ops.dead_letter_task.resolution_status IS '处理状态（open/processing/resolved/closed）';
COMMENT ON COLUMN ops.dead_letter_task.resolved_at IS '处理完成时间';
COMMENT ON COLUMN ops.dead_letter_task.created_at IS '创建时间';
COMMENT ON COLUMN ops.dead_letter_task.updated_at IS '更新时间';
