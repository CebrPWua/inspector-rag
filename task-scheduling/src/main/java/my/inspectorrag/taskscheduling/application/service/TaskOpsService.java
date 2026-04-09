package my.inspectorrag.taskscheduling.application.service;

import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import my.inspectorrag.taskscheduling.interfaces.dto.DeadLetterTaskDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskOpsService {

    private final TaskRepository taskRepository;

    public TaskOpsService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void retry(Long taskId) {
        taskRepository.retryTask(taskId);
    }

    @Transactional(readOnly = true)
    public List<DeadLetterTaskDto> deadLetterTasks() {
        return taskRepository.listDeadLetterTasks().stream()
                .map(it -> new DeadLetterTaskDto(
                        it.id(),
                        it.taskId(),
                        it.docId(),
                        it.taskType(),
                        it.lastError(),
                        it.resolutionStatus(),
                        it.createdAt()
                ))
                .toList();
    }
}
