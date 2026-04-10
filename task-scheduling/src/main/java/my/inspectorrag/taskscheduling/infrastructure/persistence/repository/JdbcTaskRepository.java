package my.inspectorrag.taskscheduling.infrastructure.persistence.repository;

import my.inspectorrag.taskscheduling.domain.model.DeadLetterTask;
import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "jdbc", matchIfMissing = true)
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ImportTask> claimTasks(int limit) {
        return jdbcTemplate.query(
                "select * from ops.claim_import_tasks(?)",
                (rs, rowNum) -> new ImportTask(
                        rs.getLong("id"),
                        rs.getLong("doc_id"),
                        rs.getString("task_type"),
                        rs.getString("task_status"),
                        rs.getInt("retry_count"),
                        rs.getInt("max_retry")
                ),
                limit
        );
    }

    @Override
    public void markTaskSuccess(Long taskId) {
        jdbcTemplate.update(
                "update ops.import_task set task_status = 'success', error_msg = null, finished_at = now() where id = ?",
                taskId
        );
    }

    @Override
    public void markTaskFailed(Long taskId, String message) {
        jdbcTemplate.update(
                "update ops.import_task set task_status = 'failed', error_msg = ?, finished_at = now() where id = ?",
                message,
                taskId
        );
    }

    @Override
    public void incrementRetry(Long taskId, String message) {
        jdbcTemplate.update(
                """
                        update ops.import_task
                           set retry_count = retry_count + 1,
                               task_status = 'retrying',
                               error_msg = ?,
                               started_at = null,
                               finished_at = null
                         where id = ?
                        """,
                message,
                taskId
        );
    }

    @Override
    public void insertRetryLog(Long id, Long taskId, int attemptNo, String errorMsg, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into ops.task_retry_log(id, task_id, attempt_no, error_msg, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
                id,
                taskId,
                attemptNo,
                errorMsg,
                now,
                now
        );
    }

    @Override
    public void moveToDeadLetter(Long id, ImportTask task, String errorMsg, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ops.dead_letter_task
                        (id, task_id, doc_id, task_type, last_error_msg, payload_json, resolution_status, created_at, updated_at)
                        values (?, ?, ?, ?, ?, '{}'::jsonb, 'open', ?, ?)
                        on conflict (task_id)
                        do update set last_error_msg = excluded.last_error_msg,
                                      updated_at = excluded.updated_at
                        """,
                id,
                task.id(),
                task.docId(),
                task.taskType(),
                errorMsg,
                now,
                now
        );
    }

    @Override
    public void retryTask(Long taskId) {
        jdbcTemplate.update(
                "update ops.import_task set task_status = 'pending', error_msg = null, started_at = null, finished_at = null where id = ?",
                taskId
        );
    }

    @Override
    public List<DeadLetterTask> listDeadLetterTasks() {
        return jdbcTemplate.query(
                """
                        select id, task_id, doc_id, task_type, last_error_msg, resolution_status, created_at
                        from ops.dead_letter_task
                        order by created_at desc
                        """,
                (rs, rowNum) -> new DeadLetterTask(
                        rs.getLong("id"),
                        rs.getLong("task_id"),
                        rs.getLong("doc_id"),
                        rs.getString("task_type"),
                        rs.getString("last_error_msg"),
                        rs.getString("resolution_status"),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );
    }
}
