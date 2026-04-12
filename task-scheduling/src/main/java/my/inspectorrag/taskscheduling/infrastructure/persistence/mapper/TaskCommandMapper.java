package my.inspectorrag.taskscheduling.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
public interface TaskCommandMapper {

    @Update("""
            update ops.import_task
               set task_status = 'success',
                   error_msg = null,
                   finished_at = now()
             where id = #{taskId}
            """)
    void markTaskSuccess(@Param("taskId") Long taskId);

    @Update("""
            update ops.import_task
               set task_status = 'failed',
                   error_msg = #{message},
                   finished_at = now()
             where id = #{taskId}
            """)
    void markTaskFailed(@Param("taskId") Long taskId, @Param("message") String message);

    @Update("""
            update ops.import_task
               set retry_count = retry_count + 1,
                   task_status = 'retrying',
                   error_msg = #{message},
                   started_at = null,
                   finished_at = null
             where id = #{taskId}
            """)
    void incrementRetry(@Param("taskId") Long taskId, @Param("message") String message);

    @Insert("""
            insert into ops.task_retry_log(id, task_id, attempt_no, error_msg, created_at, updated_at)
            values (#{id}, #{taskId}, #{attemptNo}, #{errorMsg}, #{now}, #{now})
            """)
    void insertRetryLog(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("attemptNo") int attemptNo,
            @Param("errorMsg") String errorMsg,
            @Param("now") OffsetDateTime now
    );

    @Insert("""
            insert into ops.dead_letter_task
            (id, task_id, doc_id, task_type, last_error_msg, payload_json, resolution_status, created_at, updated_at)
            values (#{id}, #{taskId}, #{docId}, #{taskType}, #{errorMsg}, '{}'::jsonb, 'open', #{now}, #{now})
            on conflict (task_id)
            do update set last_error_msg = excluded.last_error_msg,
                          updated_at = excluded.updated_at
            """)
    void upsertDeadLetterTask(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("docId") Long docId,
            @Param("taskType") String taskType,
            @Param("errorMsg") String errorMsg,
            @Param("now") OffsetDateTime now
    );

    @Update("""
            update ops.import_task
               set task_status = 'pending',
                   error_msg = null,
                   started_at = null,
                   finished_at = null
             where id = #{taskId}
            """)
    void retryTask(@Param("taskId") Long taskId);

    @Update("""
            update ops.dead_letter_task
               set resolution_status = 'processing',
                   resolved_at = null,
                   updated_at = now()
             where task_id = #{taskId}
               and resolution_status <> 'closed'
            """)
    void markDeadLetterProcessingByTaskId(@Param("taskId") Long taskId);

    @Update("""
            update ops.dead_letter_task
               set assigned_to = #{assignedTo},
                   updated_at = now()
             where id = #{deadLetterId}
            """)
    void assignDeadLetterTask(@Param("deadLetterId") Long deadLetterId, @Param("assignedTo") String assignedTo);

    @Update("""
            update ops.dead_letter_task
               set resolution_status = #{status},
                   resolved_at = case when #{status} in ('resolved', 'closed') then now() else null end,
                   updated_at = now()
             where id = #{deadLetterId}
            """)
    void updateDeadLetterTaskStatus(@Param("deadLetterId") Long deadLetterId, @Param("status") String status);
}
