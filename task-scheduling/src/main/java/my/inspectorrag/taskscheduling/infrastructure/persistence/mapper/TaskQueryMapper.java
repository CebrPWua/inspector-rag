package my.inspectorrag.taskscheduling.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskQueryMapper {

    @Select("select * from ops.claim_import_tasks(#{limit})")
    List<ImportTaskRow> claimTasks(@Param("limit") int limit);

    @Select("""
            select id, task_id, doc_id, task_type, last_error_msg, resolution_status, created_at
              from ops.dead_letter_task
             order by created_at desc
            """)
    List<DeadLetterTaskRow> listDeadLetterTasks();
}
