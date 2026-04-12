package my.inspectorrag.taskscheduling.infrastructure.persistence.repository;

import my.inspectorrag.taskscheduling.domain.model.DeadLetterTask;
import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import my.inspectorrag.taskscheduling.infrastructure.persistence.mapper.TaskCommandMapper;
import my.inspectorrag.taskscheduling.infrastructure.persistence.mapper.TaskQueryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
@ConditionalOnProperty(prefix = "inspector.persistence", name = "mode", havingValue = "mybatis")
public class MybatisTaskRepository implements TaskRepository {

    private final TaskQueryMapper queryMapper;
    private final TaskCommandMapper commandMapper;

    public MybatisTaskRepository(TaskQueryMapper queryMapper, TaskCommandMapper commandMapper) {
        this.queryMapper = queryMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    public List<ImportTask> claimTasks(int limit) {
        return queryMapper.claimTasks(limit).stream()
                .map(row -> new ImportTask(
                        row.id(),
                        row.docId(),
                        row.taskType(),
                        row.taskStatus(),
                        row.retryCount(),
                        row.maxRetry()
                ))
                .toList();
    }

    @Override
    public void markTaskSuccess(Long taskId) {
        commandMapper.markTaskSuccess(taskId);
    }

    @Override
    public void markTaskFailed(Long taskId, String message) {
        commandMapper.markTaskFailed(taskId, message);
    }

    @Override
    public void incrementRetry(Long taskId, String message) {
        commandMapper.incrementRetry(taskId, message);
    }

    @Override
    public void insertRetryLog(Long id, Long taskId, int attemptNo, String errorMsg, OffsetDateTime now) {
        commandMapper.insertRetryLog(id, taskId, attemptNo, errorMsg, now);
    }

    @Override
    public void moveToDeadLetter(Long id, ImportTask task, String errorMsg, OffsetDateTime now) {
        commandMapper.upsertDeadLetterTask(id, task.id(), task.docId(), task.taskType(), errorMsg, now);
    }

    @Override
    public void retryTask(Long taskId) {
        commandMapper.retryTask(taskId);
    }

    @Override
    public void markDeadLetterProcessingByTaskId(Long taskId) {
        commandMapper.markDeadLetterProcessingByTaskId(taskId);
    }

    @Override
    public List<DeadLetterTask> listDeadLetterTasks() {
        return queryMapper.listDeadLetterTasks().stream()
                .map(row -> new DeadLetterTask(
                        row.id(),
                        row.taskId(),
                        row.docId(),
                        row.taskType(),
                        row.lastErrorMsg(),
                        row.resolutionStatus(),
                        row.assignedTo(),
                        row.resolvedAt(),
                        row.createdAt(),
                        row.updatedAt()
                ))
                .toList();
    }

    @Override
    public Optional<DeadLetterTask> findDeadLetterTask(Long deadLetterId) {
        var row = queryMapper.findDeadLetterTask(deadLetterId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new DeadLetterTask(
                row.id(),
                row.taskId(),
                row.docId(),
                row.taskType(),
                row.lastErrorMsg(),
                row.resolutionStatus(),
                row.assignedTo(),
                row.resolvedAt(),
                row.createdAt(),
                row.updatedAt()
        ));
    }

    @Override
    public void assignDeadLetterTask(Long deadLetterId, String assignedTo) {
        commandMapper.assignDeadLetterTask(deadLetterId, assignedTo);
    }

    @Override
    public void updateDeadLetterTaskStatus(Long deadLetterId, String status) {
        commandMapper.updateDeadLetterTaskStatus(deadLetterId, status);
    }
}
