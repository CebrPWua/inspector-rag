--
-- PostgreSQL database dump
--


-- Dumped from database version 17.9 (Debian 17.9-1.pgdg12+1)
-- Dumped by pg_dump version 17.9 (Debian 17.9-1.pgdg12+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: indexing; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA indexing;


--
-- Name: ingest; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ingest;


--
-- Name: ops; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ops;


--
-- Name: retrieval; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA retrieval;


--
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION hstore IS 'data type for storing sets of (key, value) pairs';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;


--
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: import_task; Type: TABLE; Schema: ops; Owner: -
--

CREATE TABLE ops.import_task (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    task_type character varying(32) NOT NULL,
    task_status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    max_retry integer DEFAULT 3 NOT NULL,
    error_msg text,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_import_task_max_retry CHECK ((max_retry > 0)),
    CONSTRAINT ck_import_task_retry_bound CHECK ((retry_count <= max_retry)),
    CONSTRAINT ck_import_task_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT ck_import_task_status CHECK (((task_status)::text = ANY ((ARRAY['pending'::character varying, 'processing'::character varying, 'success'::character varying, 'failed'::character varying, 'retrying'::character varying])::text[]))),
    CONSTRAINT ck_import_task_time_range CHECK (((finished_at IS NULL) OR (started_at IS NULL) OR (finished_at >= started_at))),
    CONSTRAINT ck_import_task_type CHECK (((task_type)::text = ANY ((ARRAY['parse'::character varying, 'chunk'::character varying, 'embed'::character varying, 'reindex'::character varying, 'cleanup'::character varying])::text[])))
);


--
-- Name: TABLE import_task; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON TABLE ops.import_task IS '导入与处理任务主表';


--
-- Name: COLUMN import_task.id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.id IS '主键ID';


--
-- Name: COLUMN import_task.doc_id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.doc_id IS '关联法规主档ID';


--
-- Name: COLUMN import_task.task_type; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.task_type IS '任务类型（parse/chunk/embed/reindex/cleanup）';


--
-- Name: COLUMN import_task.task_status; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.task_status IS '任务状态（pending/processing/success/failed/retrying）';


--
-- Name: COLUMN import_task.retry_count; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.retry_count IS '当前重试次数';


--
-- Name: COLUMN import_task.max_retry; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.max_retry IS '最大重试次数';


--
-- Name: COLUMN import_task.error_msg; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.error_msg IS '错误信息';


--
-- Name: COLUMN import_task.started_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.started_at IS '任务开始时间';


--
-- Name: COLUMN import_task.finished_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.finished_at IS '任务结束时间';


--
-- Name: COLUMN import_task.created_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.created_at IS '创建时间';


--
-- Name: COLUMN import_task.updated_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.import_task.updated_at IS '更新时间';


--
-- Name: claim_import_tasks(integer); Type: FUNCTION; Schema: ops; Owner: -
--

CREATE FUNCTION ops.claim_import_tasks(p_limit integer DEFAULT 10) RETURNS SETOF ops.import_task
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


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: ops; Owner: -
--

CREATE FUNCTION ops.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


--
-- Name: __tmp_hv; Type: TABLE; Schema: indexing; Owner: -
--

CREATE TABLE indexing.__tmp_hv (
    embedding public.halfvec(4096)
);


--
-- Name: embedding_model; Type: TABLE; Schema: indexing; Owner: -
--

CREATE TABLE indexing.embedding_model (
    id bigint NOT NULL,
    model_name character varying(128) NOT NULL,
    dimension integer NOT NULL,
    version character varying(64) NOT NULL,
    provider character varying(64) DEFAULT 'oneapi'::character varying NOT NULL,
    is_active boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_embedding_model_dimension CHECK ((dimension > 0))
);


--
-- Name: TABLE embedding_model; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON TABLE indexing.embedding_model IS '向量模型元数据表';


--
-- Name: COLUMN embedding_model.id; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.id IS '主键ID';


--
-- Name: COLUMN embedding_model.model_name; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.model_name IS '模型名称';


--
-- Name: COLUMN embedding_model.dimension; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.dimension IS '向量维度';


--
-- Name: COLUMN embedding_model.version; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.version IS '模型版本';


--
-- Name: COLUMN embedding_model.provider; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.provider IS '模型服务提供方';


--
-- Name: COLUMN embedding_model.is_active; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.is_active IS '是否当前激活版本';


--
-- Name: COLUMN embedding_model.created_at; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.created_at IS '创建时间';


--
-- Name: COLUMN embedding_model.updated_at; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_model.updated_at IS '更新时间';


--
-- Name: embedding_profile; Type: TABLE; Schema: indexing; Owner: -
--

CREATE TABLE indexing.embedding_profile (
    id bigint NOT NULL,
    profile_key character varying(64) NOT NULL,
    provider character varying(64) NOT NULL,
    model_name character varying(128) NOT NULL,
    dimension integer NOT NULL,
    vector_type character varying(32) NOT NULL,
    distance_metric character varying(32) DEFAULT 'cosine'::character varying NOT NULL,
    storage_table character varying(128) NOT NULL,
    read_enabled boolean DEFAULT true NOT NULL,
    write_enabled boolean DEFAULT false NOT NULL,
    is_default_read boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_embedding_profile_dimension CHECK ((dimension > 0)),
    CONSTRAINT ck_embedding_profile_distance_metric CHECK (((distance_metric)::text = ANY ((ARRAY['cosine'::character varying, 'l2'::character varying, 'ip'::character varying])::text[]))),
    CONSTRAINT ck_embedding_profile_vector_type CHECK (((vector_type)::text = ANY ((ARRAY['vector'::character varying, 'halfvec'::character varying, 'sparsevec'::character varying])::text[])))
);


--
-- Name: TABLE embedding_profile; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON TABLE indexing.embedding_profile IS 'Embedding profile routing registry';


--
-- Name: COLUMN embedding_profile.profile_key; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_profile.profile_key IS '逻辑路由key';


--
-- Name: COLUMN embedding_profile.storage_table; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_profile.storage_table IS '实际向量表（schema.table）';


--
-- Name: COLUMN embedding_profile.is_default_read; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.embedding_profile.is_default_read IS '是否默认读profile';


--
-- Name: law_chunk_embedding; Type: TABLE; Schema: indexing; Owner: -
--

CREATE TABLE indexing.law_chunk_embedding (
    id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    model_id bigint NOT NULL,
    embedding_version character varying(64) NOT NULL,
    embedding public.vector(1536) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE law_chunk_embedding; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON TABLE indexing.law_chunk_embedding IS '条款向量表';


--
-- Name: COLUMN law_chunk_embedding.id; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.id IS '主键ID';


--
-- Name: COLUMN law_chunk_embedding.chunk_id; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.chunk_id IS '关联条款ID';


--
-- Name: COLUMN law_chunk_embedding.model_id; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.model_id IS '关联向量模型ID';


--
-- Name: COLUMN law_chunk_embedding.embedding_version; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.embedding_version IS '向量数据版本';


--
-- Name: COLUMN law_chunk_embedding.embedding; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.embedding IS '向量字段';


--
-- Name: COLUMN law_chunk_embedding.created_at; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.created_at IS '创建时间';


--
-- Name: COLUMN law_chunk_embedding.updated_at; Type: COMMENT; Schema: indexing; Owner: -
--

COMMENT ON COLUMN indexing.law_chunk_embedding.updated_at IS '更新时间';


--
-- Name: rag_chunk_vec_qwen3_2048_v1; Type: TABLE; Schema: indexing; Owner: -
--

CREATE TABLE indexing.rag_chunk_vec_qwen3_2048_v1 (
    id text NOT NULL,
    content text,
    metadata jsonb,
    embedding public.halfvec(2048) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chunk_tag; Type: TABLE; Schema: ingest; Owner: -
--

CREATE TABLE ingest.chunk_tag (
    id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    tag_type character varying(64) NOT NULL,
    tag_value character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE chunk_tag; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON TABLE ingest.chunk_tag IS '条款标签表';


--
-- Name: COLUMN chunk_tag.id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.id IS '主键ID';


--
-- Name: COLUMN chunk_tag.chunk_id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.chunk_id IS '关联条款ID';


--
-- Name: COLUMN chunk_tag.tag_type; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.tag_type IS '标签类型';


--
-- Name: COLUMN chunk_tag.tag_value; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.tag_value IS '标签值';


--
-- Name: COLUMN chunk_tag.created_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.created_at IS '创建时间';


--
-- Name: COLUMN chunk_tag.updated_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.chunk_tag.updated_at IS '更新时间';


--
-- Name: document_file; Type: TABLE; Schema: ingest; Owner: -
--

CREATE TABLE ingest.document_file (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    storage_path text NOT NULL,
    mime_type character varying(127) NOT NULL,
    file_size_bytes bigint NOT NULL,
    sha256 character(64) NOT NULL,
    upload_batch_no character varying(64),
    is_primary boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_document_file_sha_hex CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_document_file_size CHECK ((file_size_bytes > 0))
);


--
-- Name: TABLE document_file; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON TABLE ingest.document_file IS '法规物理文件表';


--
-- Name: COLUMN document_file.id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.id IS '主键ID';


--
-- Name: COLUMN document_file.doc_id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.doc_id IS '关联法规主档ID';


--
-- Name: COLUMN document_file.storage_path; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.storage_path IS '对象存储路径';


--
-- Name: COLUMN document_file.mime_type; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.mime_type IS 'MIME类型';


--
-- Name: COLUMN document_file.file_size_bytes; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.file_size_bytes IS '文件大小（字节）';


--
-- Name: COLUMN document_file.sha256; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.sha256 IS '文件SHA-256哈希';


--
-- Name: COLUMN document_file.upload_batch_no; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.upload_batch_no IS '上传批次号';


--
-- Name: COLUMN document_file.is_primary; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.is_primary IS '是否主文件';


--
-- Name: COLUMN document_file.created_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.created_at IS '创建时间';


--
-- Name: COLUMN document_file.updated_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.document_file.updated_at IS '更新时间';


--
-- Name: law_chunk; Type: TABLE; Schema: ingest; Owner: -
--

CREATE TABLE ingest.law_chunk (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    chapter_title character varying(255),
    section_title character varying(255),
    article_no character varying(64) NOT NULL,
    item_no character varying(64) DEFAULT ''::character varying NOT NULL,
    content text NOT NULL,
    page_start integer,
    page_end integer,
    chunk_seq integer DEFAULT 1 NOT NULL,
    content_hash character(64) NOT NULL,
    embedding_status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_law_chunk_chunk_seq CHECK ((chunk_seq > 0)),
    CONSTRAINT ck_law_chunk_content_hash_hex CHECK ((content_hash ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_law_chunk_embedding_status CHECK (((embedding_status)::text = ANY ((ARRAY['pending'::character varying, 'processing'::character varying, 'success'::character varying, 'failed'::character varying, 'skipped'::character varying])::text[]))),
    CONSTRAINT ck_law_chunk_page_range CHECK ((((page_start IS NULL) AND (page_end IS NULL)) OR ((page_start IS NOT NULL) AND (page_end IS NOT NULL) AND (page_start > 0) AND (page_end >= page_start)))),
    CONSTRAINT ck_law_chunk_status CHECK (((status)::text = ANY ((ARRAY['active'::character varying, 'inactive'::character varying])::text[])))
);


--
-- Name: TABLE law_chunk; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON TABLE ingest.law_chunk IS '法规条款切分表';


--
-- Name: COLUMN law_chunk.id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.id IS '主键ID';


--
-- Name: COLUMN law_chunk.doc_id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.doc_id IS '关联法规主档ID';


--
-- Name: COLUMN law_chunk.chapter_title; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.chapter_title IS '章标题';


--
-- Name: COLUMN law_chunk.section_title; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.section_title IS '节标题';


--
-- Name: COLUMN law_chunk.article_no; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.article_no IS '条号';


--
-- Name: COLUMN law_chunk.item_no; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.item_no IS '款/项号';


--
-- Name: COLUMN law_chunk.content; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.content IS '条款正文';


--
-- Name: COLUMN law_chunk.page_start; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.page_start IS '起始页码';


--
-- Name: COLUMN law_chunk.page_end; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.page_end IS '结束页码';


--
-- Name: COLUMN law_chunk.chunk_seq; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.chunk_seq IS '切分顺序号';


--
-- Name: COLUMN law_chunk.content_hash; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.content_hash IS '内容SHA-256哈希';


--
-- Name: COLUMN law_chunk.embedding_status; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.embedding_status IS '向量化状态（pending/processing/success/failed/skipped）';


--
-- Name: COLUMN law_chunk.status; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.status IS '条款状态（active/inactive）';


--
-- Name: COLUMN law_chunk.created_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.created_at IS '创建时间';


--
-- Name: COLUMN law_chunk.updated_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.law_chunk.updated_at IS '更新时间';


--
-- Name: source_document; Type: TABLE; Schema: ingest; Owner: -
--

CREATE TABLE ingest.source_document (
    id bigint NOT NULL,
    law_name character varying(512) NOT NULL,
    law_code character varying(128) NOT NULL,
    doc_type character varying(64) NOT NULL,
    source_file_name character varying(512) NOT NULL,
    file_hash character(64) NOT NULL,
    publish_org character varying(255),
    publish_date date,
    effective_date date,
    expired_date date,
    version_no character varying(64) NOT NULL,
    status character varying(32) DEFAULT 'active'::character varying NOT NULL,
    parse_status character varying(32) DEFAULT 'pending'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_source_document_dates CHECK (((expired_date IS NULL) OR (effective_date IS NULL) OR (expired_date >= effective_date))),
    CONSTRAINT ck_source_document_hash_hex CHECK ((file_hash ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_source_document_parse_status CHECK (((parse_status)::text = ANY ((ARRAY['pending'::character varying, 'processing'::character varying, 'success'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT ck_source_document_status CHECK (((status)::text = ANY ((ARRAY['active'::character varying, 'inactive'::character varying, 'pending_confirm'::character varying])::text[])))
);


--
-- Name: TABLE source_document; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON TABLE ingest.source_document IS '法规主档表';


--
-- Name: COLUMN source_document.id; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.id IS '主键ID';


--
-- Name: COLUMN source_document.law_name; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.law_name IS '法规名称';


--
-- Name: COLUMN source_document.law_code; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.law_code IS '法规编码/标准号';


--
-- Name: COLUMN source_document.doc_type; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.doc_type IS '文档类型（法律/条例/标准/通知等）';


--
-- Name: COLUMN source_document.source_file_name; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.source_file_name IS '原始文件名';


--
-- Name: COLUMN source_document.file_hash; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.file_hash IS '文件SHA-256哈希';


--
-- Name: COLUMN source_document.publish_org; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.publish_org IS '发布单位';


--
-- Name: COLUMN source_document.publish_date; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.publish_date IS '发布日期';


--
-- Name: COLUMN source_document.effective_date; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.effective_date IS '生效日期';


--
-- Name: COLUMN source_document.expired_date; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.expired_date IS '失效日期';


--
-- Name: COLUMN source_document.version_no; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.version_no IS '版本号';


--
-- Name: COLUMN source_document.status; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.status IS '法规状态（active/inactive/pending_confirm）';


--
-- Name: COLUMN source_document.parse_status; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.parse_status IS '解析状态（pending/processing/success/failed）';


--
-- Name: COLUMN source_document.created_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.created_at IS '创建时间';


--
-- Name: COLUMN source_document.updated_at; Type: COMMENT; Schema: ingest; Owner: -
--

COMMENT ON COLUMN ingest.source_document.updated_at IS '更新时间';


--
-- Name: dead_letter_task; Type: TABLE; Schema: ops; Owner: -
--

CREATE TABLE ops.dead_letter_task (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    doc_id bigint,
    task_type character varying(32) NOT NULL,
    last_error_msg text NOT NULL,
    payload_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    assigned_to character varying(64),
    resolution_status character varying(32) DEFAULT 'open'::character varying NOT NULL,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_dead_letter_task_last_error CHECK ((length(btrim(last_error_msg)) > 0)),
    CONSTRAINT ck_dead_letter_task_resolution_status CHECK (((resolution_status)::text = ANY ((ARRAY['open'::character varying, 'processing'::character varying, 'resolved'::character varying, 'closed'::character varying])::text[]))),
    CONSTRAINT ck_dead_letter_task_resolved_at CHECK (((resolved_at IS NULL) OR ((resolution_status)::text = ANY ((ARRAY['resolved'::character varying, 'closed'::character varying])::text[])))),
    CONSTRAINT ck_dead_letter_task_type CHECK (((task_type)::text = ANY ((ARRAY['parse'::character varying, 'chunk'::character varying, 'embed'::character varying, 'reindex'::character varying, 'cleanup'::character varying])::text[])))
);


--
-- Name: TABLE dead_letter_task; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON TABLE ops.dead_letter_task IS '死信任务表';


--
-- Name: COLUMN dead_letter_task.id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.id IS '主键ID';


--
-- Name: COLUMN dead_letter_task.task_id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.task_id IS '关联任务ID';


--
-- Name: COLUMN dead_letter_task.doc_id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.doc_id IS '关联法规主档ID';


--
-- Name: COLUMN dead_letter_task.task_type; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.task_type IS '任务类型（parse/chunk/embed/reindex/cleanup）';


--
-- Name: COLUMN dead_letter_task.last_error_msg; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.last_error_msg IS '最后一次错误信息';


--
-- Name: COLUMN dead_letter_task.payload_json; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.payload_json IS '死信上下文(JSON)';


--
-- Name: COLUMN dead_letter_task.assigned_to; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.assigned_to IS '人工处理人';


--
-- Name: COLUMN dead_letter_task.resolution_status; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.resolution_status IS '处理状态（open/processing/resolved/closed）';


--
-- Name: COLUMN dead_letter_task.resolved_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.resolved_at IS '处理完成时间';


--
-- Name: COLUMN dead_letter_task.created_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.created_at IS '创建时间';


--
-- Name: COLUMN dead_letter_task.updated_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.dead_letter_task.updated_at IS '更新时间';


--
-- Name: task_retry_log; Type: TABLE; Schema: ops; Owner: -
--

CREATE TABLE ops.task_retry_log (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    attempt_no integer NOT NULL,
    error_code character varying(64),
    error_msg text NOT NULL,
    payload_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_task_retry_log_attempt_no CHECK ((attempt_no > 0)),
    CONSTRAINT ck_task_retry_log_error_msg CHECK ((length(btrim(error_msg)) > 0))
);


--
-- Name: TABLE task_retry_log; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON TABLE ops.task_retry_log IS '任务重试日志表';


--
-- Name: COLUMN task_retry_log.id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.id IS '主键ID';


--
-- Name: COLUMN task_retry_log.task_id; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.task_id IS '关联任务ID';


--
-- Name: COLUMN task_retry_log.attempt_no; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.attempt_no IS '第几次重试';


--
-- Name: COLUMN task_retry_log.error_code; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.error_code IS '错误码';


--
-- Name: COLUMN task_retry_log.error_msg; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.error_msg IS '错误信息';


--
-- Name: COLUMN task_retry_log.payload_json; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.payload_json IS '重试上下文(JSON)';


--
-- Name: COLUMN task_retry_log.created_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.created_at IS '创建时间';


--
-- Name: COLUMN task_retry_log.updated_at; Type: COMMENT; Schema: ops; Owner: -
--

COMMENT ON COLUMN ops.task_retry_log.updated_at IS '更新时间';


--
-- Name: qa_candidate; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_candidate (
    id bigint NOT NULL,
    qa_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    source_type character varying(16) NOT NULL,
    raw_score numeric(10,6),
    rerank_score numeric(10,6),
    final_score numeric(10,6),
    rank_no integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_qa_candidate_rank_no CHECK ((rank_no > 0)),
    CONSTRAINT ck_qa_candidate_score_exists CHECK (((raw_score IS NOT NULL) OR (rerank_score IS NOT NULL) OR (final_score IS NOT NULL))),
    CONSTRAINT ck_qa_candidate_source_type CHECK (((source_type)::text = ANY ((ARRAY['vector'::character varying, 'keyword'::character varying, 'hybrid'::character varying])::text[])))
);


--
-- Name: TABLE qa_candidate; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_candidate IS '候选结果明细表';


--
-- Name: COLUMN qa_candidate.id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.id IS '主键ID';


--
-- Name: COLUMN qa_candidate.qa_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.qa_id IS '关联问答ID';


--
-- Name: COLUMN qa_candidate.chunk_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.chunk_id IS '关联条款ID';


--
-- Name: COLUMN qa_candidate.source_type; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.source_type IS '候选来源（vector/keyword/hybrid）';


--
-- Name: COLUMN qa_candidate.raw_score; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.raw_score IS '原始检索分';


--
-- Name: COLUMN qa_candidate.rerank_score; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.rerank_score IS '重排分';


--
-- Name: COLUMN qa_candidate.final_score; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.final_score IS '融合后最终分';


--
-- Name: COLUMN qa_candidate.rank_no; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.rank_no IS '候选排序名次';


--
-- Name: COLUMN qa_candidate.created_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.created_at IS '创建时间';


--
-- Name: COLUMN qa_candidate.updated_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_candidate.updated_at IS '更新时间';


--
-- Name: qa_conversation; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_conversation (
    id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE qa_conversation; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_conversation IS '问答会话主表';


--
-- Name: qa_evidence; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_evidence (
    id bigint NOT NULL,
    qa_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    cite_no integer NOT NULL,
    quoted_text text NOT NULL,
    used_in_answer boolean DEFAULT true NOT NULL,
    law_name character varying(512),
    article_no character varying(64),
    page_start integer,
    page_end integer,
    file_version character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_qa_evidence_cite_no CHECK ((cite_no > 0)),
    CONSTRAINT ck_qa_evidence_page_range CHECK ((((page_start IS NULL) AND (page_end IS NULL)) OR ((page_start IS NOT NULL) AND (page_end IS NOT NULL) AND (page_start > 0) AND (page_end >= page_start)))),
    CONSTRAINT ck_qa_evidence_quoted_text CHECK ((length(btrim(quoted_text)) > 0))
);


--
-- Name: TABLE qa_evidence; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_evidence IS '最终证据明细表';


--
-- Name: COLUMN qa_evidence.id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.id IS '主键ID';


--
-- Name: COLUMN qa_evidence.qa_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.qa_id IS '关联问答ID';


--
-- Name: COLUMN qa_evidence.chunk_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.chunk_id IS '关联条款ID';


--
-- Name: COLUMN qa_evidence.cite_no; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.cite_no IS '引用序号';


--
-- Name: COLUMN qa_evidence.quoted_text; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.quoted_text IS '引用原文片段';


--
-- Name: COLUMN qa_evidence.used_in_answer; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.used_in_answer IS '是否用于最终回答';


--
-- Name: COLUMN qa_evidence.law_name; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.law_name IS '法规名称快照';


--
-- Name: COLUMN qa_evidence.article_no; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.article_no IS '条号快照';


--
-- Name: COLUMN qa_evidence.page_start; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.page_start IS '起始页码快照';


--
-- Name: COLUMN qa_evidence.page_end; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.page_end IS '结束页码快照';


--
-- Name: COLUMN qa_evidence.file_version; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.file_version IS '文件版本快照';


--
-- Name: COLUMN qa_evidence.created_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.created_at IS '创建时间';


--
-- Name: COLUMN qa_evidence.updated_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_evidence.updated_at IS '更新时间';


--
-- Name: qa_record; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_record (
    id bigint NOT NULL,
    question text NOT NULL,
    normalized_question text NOT NULL,
    answer text,
    answer_status character varying(32) NOT NULL,
    reject_reason text,
    elapsed_ms integer,
    user_feedback character varying(16) DEFAULT 'unrated'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    rewritten_question text,
    conversation_id bigint,
    turn_no integer,
    CONSTRAINT ck_qa_record_answer_for_success CHECK ((((answer_status)::text <> 'success'::text) OR ((answer IS NOT NULL) AND (length(btrim(answer)) > 0)))),
    CONSTRAINT ck_qa_record_elapsed CHECK (((elapsed_ms IS NULL) OR (elapsed_ms >= 0))),
    CONSTRAINT ck_qa_record_feedback CHECK (((user_feedback)::text = ANY ((ARRAY['useful'::character varying, 'useless'::character varying, 'unrated'::character varying])::text[]))),
    CONSTRAINT ck_qa_record_reject_reason CHECK ((((answer_status)::text <> 'reject'::text) OR ((reject_reason IS NOT NULL) AND (length(btrim(reject_reason)) > 0)))),
    CONSTRAINT ck_qa_record_status CHECK (((answer_status)::text = ANY ((ARRAY['success'::character varying, 'reject'::character varying, 'failed'::character varying])::text[])))
);


--
-- Name: TABLE qa_record; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_record IS '问答记录主表';


--
-- Name: COLUMN qa_record.id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.id IS '主键ID';


--
-- Name: COLUMN qa_record.question; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.question IS '用户原始问题';


--
-- Name: COLUMN qa_record.normalized_question; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.normalized_question IS '标准化后的问题';


--
-- Name: COLUMN qa_record.answer; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.answer IS '最终回答';


--
-- Name: COLUMN qa_record.answer_status; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.answer_status IS '回答状态（success/reject/failed）';


--
-- Name: COLUMN qa_record.reject_reason; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.reject_reason IS '拒答原因';


--
-- Name: COLUMN qa_record.elapsed_ms; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.elapsed_ms IS '总耗时（毫秒）';


--
-- Name: COLUMN qa_record.user_feedback; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.user_feedback IS '用户反馈（useful/useless/unrated）';


--
-- Name: COLUMN qa_record.created_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.created_at IS '创建时间';


--
-- Name: COLUMN qa_record.updated_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.updated_at IS '更新时间';


--
-- Name: COLUMN qa_record.rewritten_question; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.rewritten_question IS 'LLM改写后的问题';


--
-- Name: COLUMN qa_record.conversation_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.conversation_id IS '所属会话ID';


--
-- Name: COLUMN qa_record.turn_no; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_record.turn_no IS '会话内轮次（从1开始）';


--
-- Name: qa_reject_threshold_config; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_reject_threshold_config (
    id integer NOT NULL,
    min_top1_score numeric(6,4) NOT NULL,
    min_top1_score_vector_only numeric(6,4) NOT NULL,
    min_top_gap numeric(6,4) NOT NULL,
    min_confident_score numeric(6,4) NOT NULL,
    min_evidence_count integer NOT NULL,
    updated_by character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_qa_reject_threshold_config_confident CHECK (((min_confident_score >= (0)::numeric) AND (min_confident_score <= (1)::numeric))),
    CONSTRAINT ck_qa_reject_threshold_config_evidence_count CHECK ((min_evidence_count >= 1)),
    CONSTRAINT ck_qa_reject_threshold_config_singleton CHECK ((id = 1)),
    CONSTRAINT ck_qa_reject_threshold_config_top1 CHECK (((min_top1_score >= (0)::numeric) AND (min_top1_score <= (1)::numeric))),
    CONSTRAINT ck_qa_reject_threshold_config_top1_vector CHECK (((min_top1_score_vector_only >= (0)::numeric) AND (min_top1_score_vector_only <= (1)::numeric))),
    CONSTRAINT ck_qa_reject_threshold_config_top_gap CHECK (((min_top_gap >= (0)::numeric) AND (min_top_gap <= (1)::numeric)))
);


--
-- Name: TABLE qa_reject_threshold_config; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_reject_threshold_config IS '问答拒答阈值运行时配置（单行）';


--
-- Name: COLUMN qa_reject_threshold_config.id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.id IS '固定主键（=1）';


--
-- Name: COLUMN qa_reject_threshold_config.min_top1_score; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.min_top1_score IS '混合/关键词场景Top1最小阈值';


--
-- Name: COLUMN qa_reject_threshold_config.min_top1_score_vector_only; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.min_top1_score_vector_only IS '纯向量场景Top1最小阈值';


--
-- Name: COLUMN qa_reject_threshold_config.min_top_gap; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.min_top_gap IS 'Top1-Top2最小分差阈值';


--
-- Name: COLUMN qa_reject_threshold_config.min_confident_score; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.min_confident_score IS '判定分差时的Top1可信阈值';


--
-- Name: COLUMN qa_reject_threshold_config.min_evidence_count; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.min_evidence_count IS '最小证据条数阈值';


--
-- Name: COLUMN qa_reject_threshold_config.updated_by; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.updated_by IS '最近更新操作者';


--
-- Name: COLUMN qa_reject_threshold_config.created_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.created_at IS '创建时间';


--
-- Name: COLUMN qa_reject_threshold_config.updated_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_reject_threshold_config.updated_at IS '更新时间';


--
-- Name: qa_retrieval_snapshot; Type: TABLE; Schema: retrieval; Owner: -
--

CREATE TABLE retrieval.qa_retrieval_snapshot (
    id bigint NOT NULL,
    qa_id bigint NOT NULL,
    filters_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    query_embedding_model character varying(128) NOT NULL,
    topk_requested integer NOT NULL,
    topn_returned integer,
    keyword_query text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    effective_query text,
    rewrite_queries_json jsonb,
    query_embedding_profile character varying(64),
    CONSTRAINT ck_qa_snapshot_topk CHECK ((topk_requested > 0)),
    CONSTRAINT ck_qa_snapshot_topn CHECK (((topn_returned IS NULL) OR (topn_returned >= 0)))
);


--
-- Name: TABLE qa_retrieval_snapshot; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON TABLE retrieval.qa_retrieval_snapshot IS '检索快照表';


--
-- Name: COLUMN qa_retrieval_snapshot.id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.id IS '主键ID';


--
-- Name: COLUMN qa_retrieval_snapshot.qa_id; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.qa_id IS '关联问答ID';


--
-- Name: COLUMN qa_retrieval_snapshot.filters_json; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.filters_json IS '检索过滤条件快照(JSON)';


--
-- Name: COLUMN qa_retrieval_snapshot.query_embedding_model; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.query_embedding_model IS '查询向量模型名称';


--
-- Name: COLUMN qa_retrieval_snapshot.topk_requested; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.topk_requested IS '请求召回TopK';


--
-- Name: COLUMN qa_retrieval_snapshot.topn_returned; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.topn_returned IS '最终返回TopN';


--
-- Name: COLUMN qa_retrieval_snapshot.keyword_query; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.keyword_query IS '关键词检索串';


--
-- Name: COLUMN qa_retrieval_snapshot.created_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.created_at IS '创建时间';


--
-- Name: COLUMN qa_retrieval_snapshot.updated_at; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.updated_at IS '更新时间';


--
-- Name: COLUMN qa_retrieval_snapshot.effective_query; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.effective_query IS '本次检索实际使用的主查询';


--
-- Name: COLUMN qa_retrieval_snapshot.rewrite_queries_json; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.rewrite_queries_json IS 'LLM改写出的候选查询列表(JSON)';


--
-- Name: COLUMN qa_retrieval_snapshot.query_embedding_profile; Type: COMMENT; Schema: retrieval; Owner: -
--

COMMENT ON COLUMN retrieval.qa_retrieval_snapshot.query_embedding_profile IS '查询使用的embedding profile';


--
-- Name: embedding_model embedding_model_pkey; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.embedding_model
    ADD CONSTRAINT embedding_model_pkey PRIMARY KEY (id);


--
-- Name: embedding_profile embedding_profile_pkey; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.embedding_profile
    ADD CONSTRAINT embedding_profile_pkey PRIMARY KEY (id);


--
-- Name: law_chunk_embedding law_chunk_embedding_pkey; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.law_chunk_embedding
    ADD CONSTRAINT law_chunk_embedding_pkey PRIMARY KEY (id);


--
-- Name: rag_chunk_vec_qwen3_2048_v1 rag_chunk_vec_qwen3_2048_v1_pkey; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.rag_chunk_vec_qwen3_2048_v1
    ADD CONSTRAINT rag_chunk_vec_qwen3_2048_v1_pkey PRIMARY KEY (id);


--
-- Name: embedding_model uk_embedding_model; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.embedding_model
    ADD CONSTRAINT uk_embedding_model UNIQUE (model_name, version);


--
-- Name: embedding_profile uk_embedding_profile_key; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.embedding_profile
    ADD CONSTRAINT uk_embedding_profile_key UNIQUE (profile_key);


--
-- Name: law_chunk_embedding uk_law_chunk_embedding; Type: CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.law_chunk_embedding
    ADD CONSTRAINT uk_law_chunk_embedding UNIQUE (chunk_id, model_id, embedding_version);


--
-- Name: chunk_tag chunk_tag_pkey; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.chunk_tag
    ADD CONSTRAINT chunk_tag_pkey PRIMARY KEY (id);


--
-- Name: document_file document_file_pkey; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.document_file
    ADD CONSTRAINT document_file_pkey PRIMARY KEY (id);


--
-- Name: law_chunk law_chunk_pkey; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.law_chunk
    ADD CONSTRAINT law_chunk_pkey PRIMARY KEY (id);


--
-- Name: source_document source_document_pkey; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.source_document
    ADD CONSTRAINT source_document_pkey PRIMARY KEY (id);


--
-- Name: chunk_tag uk_chunk_tag; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.chunk_tag
    ADD CONSTRAINT uk_chunk_tag UNIQUE (chunk_id, tag_type, tag_value);


--
-- Name: document_file uk_document_file_doc_sha; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.document_file
    ADD CONSTRAINT uk_document_file_doc_sha UNIQUE (doc_id, sha256);


--
-- Name: law_chunk uk_law_chunk_loc; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.law_chunk
    ADD CONSTRAINT uk_law_chunk_loc UNIQUE (doc_id, article_no, item_no, chunk_seq);


--
-- Name: source_document uk_source_document_file_hash; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.source_document
    ADD CONSTRAINT uk_source_document_file_hash UNIQUE (file_hash);


--
-- Name: source_document uk_source_document_law_version; Type: CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.source_document
    ADD CONSTRAINT uk_source_document_law_version UNIQUE (law_code, version_no);


--
-- Name: dead_letter_task dead_letter_task_pkey; Type: CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.dead_letter_task
    ADD CONSTRAINT dead_letter_task_pkey PRIMARY KEY (id);


--
-- Name: import_task import_task_pkey; Type: CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.import_task
    ADD CONSTRAINT import_task_pkey PRIMARY KEY (id);


--
-- Name: task_retry_log task_retry_log_pkey; Type: CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.task_retry_log
    ADD CONSTRAINT task_retry_log_pkey PRIMARY KEY (id);


--
-- Name: dead_letter_task uk_dead_letter_task_task; Type: CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.dead_letter_task
    ADD CONSTRAINT uk_dead_letter_task_task UNIQUE (task_id);


--
-- Name: task_retry_log uk_task_retry_log_attempt; Type: CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.task_retry_log
    ADD CONSTRAINT uk_task_retry_log_attempt UNIQUE (task_id, attempt_no);


--
-- Name: qa_candidate qa_candidate_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_candidate
    ADD CONSTRAINT qa_candidate_pkey PRIMARY KEY (id);


--
-- Name: qa_conversation qa_conversation_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_conversation
    ADD CONSTRAINT qa_conversation_pkey PRIMARY KEY (id);


--
-- Name: qa_evidence qa_evidence_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_evidence
    ADD CONSTRAINT qa_evidence_pkey PRIMARY KEY (id);


--
-- Name: qa_record qa_record_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_record
    ADD CONSTRAINT qa_record_pkey PRIMARY KEY (id);


--
-- Name: qa_reject_threshold_config qa_reject_threshold_config_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_reject_threshold_config
    ADD CONSTRAINT qa_reject_threshold_config_pkey PRIMARY KEY (id);


--
-- Name: qa_retrieval_snapshot qa_retrieval_snapshot_pkey; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_retrieval_snapshot
    ADD CONSTRAINT qa_retrieval_snapshot_pkey PRIMARY KEY (id);


--
-- Name: qa_evidence uk_qa_evidence_qa_cite; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_evidence
    ADD CONSTRAINT uk_qa_evidence_qa_cite UNIQUE (qa_id, cite_no);


--
-- Name: qa_retrieval_snapshot uk_qa_snapshot_one_per_qa; Type: CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_retrieval_snapshot
    ADD CONSTRAINT uk_qa_snapshot_one_per_qa UNIQUE (qa_id);


--
-- Name: __tmp_hv_bq_idx; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX __tmp_hv_bq_idx ON indexing.__tmp_hv USING hnsw (((public.binary_quantize(embedding))::bit(4096)) public.bit_hamming_ops);


--
-- Name: idx_law_chunk_embedding_chunk_id; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX idx_law_chunk_embedding_chunk_id ON indexing.law_chunk_embedding USING btree (chunk_id);


--
-- Name: idx_law_chunk_embedding_model_id; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX idx_law_chunk_embedding_model_id ON indexing.law_chunk_embedding USING btree (model_id, created_at DESC);


--
-- Name: idx_law_chunk_embedding_vector_cosine; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX idx_law_chunk_embedding_vector_cosine ON indexing.law_chunk_embedding USING ivfflat (embedding public.vector_cosine_ops) WITH (lists='100');


--
-- Name: idx_rag_chunk_vec_qwen3_2048_v1_hnsw_cosine; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX idx_rag_chunk_vec_qwen3_2048_v1_hnsw_cosine ON indexing.rag_chunk_vec_qwen3_2048_v1 USING hnsw (embedding public.halfvec_cosine_ops);


--
-- Name: idx_rag_chunk_vec_qwen3_2048_v1_metadata_gin; Type: INDEX; Schema: indexing; Owner: -
--

CREATE INDEX idx_rag_chunk_vec_qwen3_2048_v1_metadata_gin ON indexing.rag_chunk_vec_qwen3_2048_v1 USING gin (metadata);


--
-- Name: uk_embedding_model_active_one_per_name; Type: INDEX; Schema: indexing; Owner: -
--

CREATE UNIQUE INDEX uk_embedding_model_active_one_per_name ON indexing.embedding_model USING btree (model_name) WHERE is_active;


--
-- Name: uk_embedding_profile_default_read; Type: INDEX; Schema: indexing; Owner: -
--

CREATE UNIQUE INDEX uk_embedding_profile_default_read ON indexing.embedding_profile USING btree (is_default_read) WHERE is_default_read;


--
-- Name: idx_chunk_tag_type_value; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_chunk_tag_type_value ON ingest.chunk_tag USING btree (tag_type, tag_value);


--
-- Name: idx_document_file_doc_id; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_document_file_doc_id ON ingest.document_file USING btree (doc_id);


--
-- Name: idx_law_chunk_content_hash; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_law_chunk_content_hash ON ingest.law_chunk USING btree (content_hash);


--
-- Name: idx_law_chunk_doc_status; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_law_chunk_doc_status ON ingest.law_chunk USING btree (doc_id, status);


--
-- Name: idx_law_chunk_embedding_status; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_law_chunk_embedding_status ON ingest.law_chunk USING btree (embedding_status, updated_at);


--
-- Name: idx_source_document_effective_date; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_source_document_effective_date ON ingest.source_document USING btree (effective_date);


--
-- Name: idx_source_document_law_code; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_source_document_law_code ON ingest.source_document USING btree (law_code);


--
-- Name: idx_source_document_status; Type: INDEX; Schema: ingest; Owner: -
--

CREATE INDEX idx_source_document_status ON ingest.source_document USING btree (status);


--
-- Name: uk_document_file_primary_per_doc; Type: INDEX; Schema: ingest; Owner: -
--

CREATE UNIQUE INDEX uk_document_file_primary_per_doc ON ingest.document_file USING btree (doc_id) WHERE is_primary;


--
-- Name: idx_dead_letter_payload_gin; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_dead_letter_payload_gin ON ops.dead_letter_task USING gin (payload_json);


--
-- Name: idx_dead_letter_status_created_at; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_dead_letter_status_created_at ON ops.dead_letter_task USING btree (resolution_status, created_at DESC);


--
-- Name: idx_import_task_claimable; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_import_task_claimable ON ops.import_task USING btree (created_at, id) WHERE ((task_status)::text = ANY ((ARRAY['pending'::character varying, 'retrying'::character varying])::text[]));


--
-- Name: idx_import_task_doc_type_created_at; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_import_task_doc_type_created_at ON ops.import_task USING btree (doc_id, task_type, created_at DESC);


--
-- Name: idx_import_task_status_created_at; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_import_task_status_created_at ON ops.import_task USING btree (task_status, created_at);


--
-- Name: idx_task_retry_log_payload_gin; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_task_retry_log_payload_gin ON ops.task_retry_log USING gin (payload_json);


--
-- Name: idx_task_retry_log_task_id; Type: INDEX; Schema: ops; Owner: -
--

CREATE INDEX idx_task_retry_log_task_id ON ops.task_retry_log USING btree (task_id);


--
-- Name: idx_qa_candidate_chunk_id; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_candidate_chunk_id ON retrieval.qa_candidate USING btree (chunk_id);


--
-- Name: idx_qa_candidate_qa_rank; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_candidate_qa_rank ON retrieval.qa_candidate USING btree (qa_id, rank_no);


--
-- Name: idx_qa_evidence_chunk_id; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_evidence_chunk_id ON retrieval.qa_evidence USING btree (chunk_id);


--
-- Name: idx_qa_evidence_qa_cite; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_evidence_qa_cite ON retrieval.qa_evidence USING btree (qa_id, cite_no);


--
-- Name: idx_qa_record_conversation_turn; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_record_conversation_turn ON retrieval.qa_record USING btree (conversation_id, turn_no);


--
-- Name: idx_qa_record_created_at_desc; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_record_created_at_desc ON retrieval.qa_record USING btree (created_at DESC);


--
-- Name: idx_qa_record_status_created_at; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_record_status_created_at ON retrieval.qa_record USING btree (answer_status, created_at DESC);


--
-- Name: idx_qa_snapshot_filters_gin; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE INDEX idx_qa_snapshot_filters_gin ON retrieval.qa_retrieval_snapshot USING gin (filters_json);


--
-- Name: uk_qa_candidate_qa_rank; Type: INDEX; Schema: retrieval; Owner: -
--

CREATE UNIQUE INDEX uk_qa_candidate_qa_rank ON retrieval.qa_candidate USING btree (qa_id, rank_no);


--
-- Name: embedding_model trg_embedding_model_updated_at; Type: TRIGGER; Schema: indexing; Owner: -
--

CREATE TRIGGER trg_embedding_model_updated_at BEFORE UPDATE ON indexing.embedding_model FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: embedding_profile trg_embedding_profile_updated_at; Type: TRIGGER; Schema: indexing; Owner: -
--

CREATE TRIGGER trg_embedding_profile_updated_at BEFORE UPDATE ON indexing.embedding_profile FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: law_chunk_embedding trg_law_chunk_embedding_updated_at; Type: TRIGGER; Schema: indexing; Owner: -
--

CREATE TRIGGER trg_law_chunk_embedding_updated_at BEFORE UPDATE ON indexing.law_chunk_embedding FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: rag_chunk_vec_qwen3_2048_v1 trg_rag_chunk_vec_qwen3_2048_v1_updated_at; Type: TRIGGER; Schema: indexing; Owner: -
--

CREATE TRIGGER trg_rag_chunk_vec_qwen3_2048_v1_updated_at BEFORE UPDATE ON indexing.rag_chunk_vec_qwen3_2048_v1 FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: chunk_tag trg_chunk_tag_updated_at; Type: TRIGGER; Schema: ingest; Owner: -
--

CREATE TRIGGER trg_chunk_tag_updated_at BEFORE UPDATE ON ingest.chunk_tag FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: document_file trg_document_file_updated_at; Type: TRIGGER; Schema: ingest; Owner: -
--

CREATE TRIGGER trg_document_file_updated_at BEFORE UPDATE ON ingest.document_file FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: law_chunk trg_law_chunk_updated_at; Type: TRIGGER; Schema: ingest; Owner: -
--

CREATE TRIGGER trg_law_chunk_updated_at BEFORE UPDATE ON ingest.law_chunk FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: source_document trg_source_document_updated_at; Type: TRIGGER; Schema: ingest; Owner: -
--

CREATE TRIGGER trg_source_document_updated_at BEFORE UPDATE ON ingest.source_document FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: dead_letter_task trg_dead_letter_task_updated_at; Type: TRIGGER; Schema: ops; Owner: -
--

CREATE TRIGGER trg_dead_letter_task_updated_at BEFORE UPDATE ON ops.dead_letter_task FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: import_task trg_import_task_updated_at; Type: TRIGGER; Schema: ops; Owner: -
--

CREATE TRIGGER trg_import_task_updated_at BEFORE UPDATE ON ops.import_task FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: task_retry_log trg_task_retry_log_updated_at; Type: TRIGGER; Schema: ops; Owner: -
--

CREATE TRIGGER trg_task_retry_log_updated_at BEFORE UPDATE ON ops.task_retry_log FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: qa_candidate trg_qa_candidate_updated_at; Type: TRIGGER; Schema: retrieval; Owner: -
--

CREATE TRIGGER trg_qa_candidate_updated_at BEFORE UPDATE ON retrieval.qa_candidate FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: qa_evidence trg_qa_evidence_updated_at; Type: TRIGGER; Schema: retrieval; Owner: -
--

CREATE TRIGGER trg_qa_evidence_updated_at BEFORE UPDATE ON retrieval.qa_evidence FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: qa_record trg_qa_record_updated_at; Type: TRIGGER; Schema: retrieval; Owner: -
--

CREATE TRIGGER trg_qa_record_updated_at BEFORE UPDATE ON retrieval.qa_record FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: qa_retrieval_snapshot trg_qa_retrieval_snapshot_updated_at; Type: TRIGGER; Schema: retrieval; Owner: -
--

CREATE TRIGGER trg_qa_retrieval_snapshot_updated_at BEFORE UPDATE ON retrieval.qa_retrieval_snapshot FOR EACH ROW EXECUTE FUNCTION ops.set_updated_at();


--
-- Name: law_chunk_embedding fk_law_chunk_embedding_chunk; Type: FK CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.law_chunk_embedding
    ADD CONSTRAINT fk_law_chunk_embedding_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk(id) ON DELETE CASCADE;


--
-- Name: law_chunk_embedding fk_law_chunk_embedding_model; Type: FK CONSTRAINT; Schema: indexing; Owner: -
--

ALTER TABLE ONLY indexing.law_chunk_embedding
    ADD CONSTRAINT fk_law_chunk_embedding_model FOREIGN KEY (model_id) REFERENCES indexing.embedding_model(id);


--
-- Name: chunk_tag fk_chunk_tag_chunk; Type: FK CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.chunk_tag
    ADD CONSTRAINT fk_chunk_tag_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk(id) ON DELETE CASCADE;


--
-- Name: document_file fk_document_file_doc; Type: FK CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.document_file
    ADD CONSTRAINT fk_document_file_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document(id) ON DELETE CASCADE;


--
-- Name: law_chunk fk_law_chunk_doc; Type: FK CONSTRAINT; Schema: ingest; Owner: -
--

ALTER TABLE ONLY ingest.law_chunk
    ADD CONSTRAINT fk_law_chunk_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document(id) ON DELETE CASCADE;


--
-- Name: dead_letter_task fk_dead_letter_task_doc; Type: FK CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.dead_letter_task
    ADD CONSTRAINT fk_dead_letter_task_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document(id) ON DELETE SET NULL;


--
-- Name: dead_letter_task fk_dead_letter_task_task; Type: FK CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.dead_letter_task
    ADD CONSTRAINT fk_dead_letter_task_task FOREIGN KEY (task_id) REFERENCES ops.import_task(id) ON DELETE CASCADE;


--
-- Name: import_task fk_import_task_doc; Type: FK CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.import_task
    ADD CONSTRAINT fk_import_task_doc FOREIGN KEY (doc_id) REFERENCES ingest.source_document(id) ON DELETE CASCADE;


--
-- Name: task_retry_log fk_task_retry_log_task; Type: FK CONSTRAINT; Schema: ops; Owner: -
--

ALTER TABLE ONLY ops.task_retry_log
    ADD CONSTRAINT fk_task_retry_log_task FOREIGN KEY (task_id) REFERENCES ops.import_task(id) ON DELETE CASCADE;


--
-- Name: qa_candidate fk_qa_candidate_chunk; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_candidate
    ADD CONSTRAINT fk_qa_candidate_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk(id);


--
-- Name: qa_candidate fk_qa_candidate_qa; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_candidate
    ADD CONSTRAINT fk_qa_candidate_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record(id) ON DELETE CASCADE;


--
-- Name: qa_evidence fk_qa_evidence_chunk; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_evidence
    ADD CONSTRAINT fk_qa_evidence_chunk FOREIGN KEY (chunk_id) REFERENCES ingest.law_chunk(id);


--
-- Name: qa_evidence fk_qa_evidence_qa; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_evidence
    ADD CONSTRAINT fk_qa_evidence_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record(id) ON DELETE CASCADE;


--
-- Name: qa_record fk_qa_record_conversation; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_record
    ADD CONSTRAINT fk_qa_record_conversation FOREIGN KEY (conversation_id) REFERENCES retrieval.qa_conversation(id) ON DELETE SET NULL;


--
-- Name: qa_retrieval_snapshot fk_qa_snapshot_qa; Type: FK CONSTRAINT; Schema: retrieval; Owner: -
--

ALTER TABLE ONLY retrieval.qa_retrieval_snapshot
    ADD CONSTRAINT fk_qa_snapshot_qa FOREIGN KEY (qa_id) REFERENCES retrieval.qa_record(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


