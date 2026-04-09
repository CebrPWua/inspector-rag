# Database Scripts

## Files
- `src/main/resources/db/migration/V1__init_inspector_rag.sql`
  - Baseline DDL for PostgreSQL 17.
  - Creates extensions, schemas, tables, constraints, indexes, triggers, and task-claim function.
- `database/acceptance/verify_v1.sql`
  - Acceptance verification script for key constraints and workflows.
  - Uses `BEGIN ... ROLLBACK` so it does not persist test data.

## Schemas
- `ingest`: file/document/chunk/tag
- `indexing`: embedding model and vectors
- `retrieval`: QA records, retrieval snapshots, candidates, evidences
- `ops`: import tasks, retry log, dead-letter tasks

## Run Manually
```bash
psql -d inspector_rag -f src/main/resources/db/migration/V1__init_inspector_rag.sql
psql -d inspector_rag -f database/acceptance/verify_v1.sql
```

## Notes
- Extensions required: `vector`, `pg_trgm`, `unaccent`.
- Vector dimension is fixed to `1536` in V1 (`text-embedding-3-small`).
