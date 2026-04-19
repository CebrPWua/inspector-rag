-- Backfill parse_status for documents whose parse task already reached dead letter.
-- This migration is idempotent and only updates pending/processing records.
update ingest.source_document d
set parse_status = 'failed',
    updated_at = now()
where d.parse_status in ('pending', 'processing')
  and exists (
    select 1
    from ops.dead_letter_task dl
    join ops.import_task t on t.id = dl.task_id
    where dl.doc_id = d.id
      and dl.task_type = 'parse'
      and (
        t.task_status = 'failed'
        or dl.resolution_status in ('resolved', 'closed')
      )
  );
