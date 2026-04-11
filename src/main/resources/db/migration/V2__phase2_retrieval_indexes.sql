-- Phase 2 retrieval enhancement indexes
-- improve FTS + fuzzy lookup performance for law_chunk content

CREATE INDEX IF NOT EXISTS idx_law_chunk_content_tsv_simple
    ON ingest.law_chunk USING gin (to_tsvector('simple', coalesce(content, '')));

CREATE INDEX IF NOT EXISTS idx_law_chunk_content_trgm
    ON ingest.law_chunk USING gin (content gin_trgm_ops);
