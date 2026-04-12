package my.inspectorrag.taskscheduling.domain.repository;

import my.inspectorrag.taskscheduling.domain.model.DeadLetterTask;
import my.inspectorrag.taskscheduling.domain.model.ImportTask;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    List<ImportTask> claimTasks(int limit);

    void markTaskSuccess(Long taskId);

    void markTaskFailed(Long taskId, String message);

    void incrementRetry(Long taskId, String message);

    void insertRetryLog(Long id, Long taskId, int attemptNo, String errorMsg, OffsetDateTime now);

    void moveToDeadLetter(Long id, ImportTask task, String errorMsg, OffsetDateTime now);

    void retryTask(Long taskId);

    void markDeadLetterProcessingByTaskId(Long taskId);

    List<DeadLetterTask> listDeadLetterTasks();

    Optional<DeadLetterTask> findDeadLetterTask(Long deadLetterId);

    void assignDeadLetterTask(Long deadLetterId, String assignedTo);

    void updateDeadLetterTaskStatus(Long deadLetterId, String status);
}
