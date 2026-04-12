package my.inspectorrag.taskscheduling.infrastructure.persistence.repository;

import my.inspectorrag.taskscheduling.domain.model.DeadLetterTask;
import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    public void markDeadLetterProcessingByTaskId(Long taskId) {
        jdbcTemplate.update(
                """
                        update ops.dead_letter_task
                           set resolution_status = 'processing',
                               resolved_at = null,
                               updated_at = now()
                         where task_id = ?
                           and resolution_status <> 'closed'
                        """,
                taskId
        );
    }

    @Override
    public List<DeadLetterTask> listDeadLetterTasks() {
        return jdbcTemplate.query(
                """
                        select id, task_id, doc_id, task_type, last_error_msg, resolution_status,
                               assigned_to, resolved_at, created_at, updated_at
                        from ops.dead_letter_task
                        order by updated_at desc, created_at desc
                        """,
                (rs, rowNum) -> new DeadLetterTask(
                        rs.getLong("id"),
                        rs.getLong("task_id"),
                        rs.getLong("doc_id"),
                        rs.getString("task_type"),
                        rs.getString("last_error_msg"),
                        rs.getString("resolution_status"),
                        rs.getString("assigned_to"),
                        rs.getObject("resolved_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                )
        );
    }

    @Override
    public Optional<DeadLetterTask> findDeadLetterTask(Long deadLetterId) {
        return jdbcTemplate.query(
                """
                        select id, task_id, doc_id, task_type, last_error_msg, resolution_status,
                               assigned_to, resolved_at, created_at, updated_at
                          from ops.dead_letter_task
                         where id = ?
                        """,
                (rs, rowNum) -> new DeadLetterTask(
                        rs.getLong("id"),
                        rs.getLong("task_id"),
                        rs.getLong("doc_id"),
                        rs.getString("task_type"),
                        rs.getString("last_error_msg"),
                        rs.getString("resolution_status"),
                        rs.getString("assigned_to"),
                        rs.getObject("resolved_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                deadLetterId
        ).stream().findFirst();
    }

    @Override
    public void assignDeadLetterTask(Long deadLetterId, String assignedTo) {
        jdbcTemplate.update(
                """
                        update ops.dead_letter_task
                           set assigned_to = ?,
                               updated_at = now()
                         where id = ?
                        """,
                assignedTo,
                deadLetterId
        );
    }

    @Override
    public void updateDeadLetterTaskStatus(Long deadLetterId, String status) {
        jdbcTemplate.update(
                """
                        update ops.dead_letter_task
                           set resolution_status = ?,
                               resolved_at = case when ? in ('resolved', 'closed') then now() else null end,
                               updated_at = now()
                         where id = ?
                        """,
                status,
                status,
                deadLetterId
        );
    }
}
