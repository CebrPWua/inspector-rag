package my.inspectorrag.taskscheduling.application.service;

import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import my.inspectorrag.taskscheduling.interfaces.dto.DeadLetterTaskDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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
        taskRepository.markDeadLetterProcessingByTaskId(taskId);
        taskRepository.markParseDocumentPendingForRetry(taskId);
    }

    @Transactional
    public void assignDeadLetter(Long deadLetterId, String assignedTo) {
        String assignee = assignedTo == null ? "" : assignedTo.trim();
        if (assignee.isEmpty()) {
            throw new IllegalArgumentException("assignedTo must not be blank");
        }
        if (taskRepository.findDeadLetterTask(deadLetterId).isEmpty()) {
            throw new IllegalArgumentException("dead letter task not found: " + deadLetterId);
        }
        taskRepository.assignDeadLetterTask(deadLetterId, assignee);
    }

    @Transactional
    public void updateDeadLetterStatus(Long deadLetterId, String targetStatus) {
        String normalizedStatus = normalizeStatus(targetStatus);
        var task = taskRepository.findDeadLetterTask(deadLetterId)
                .orElseThrow(() -> new IllegalArgumentException("dead letter task not found: " + deadLetterId));
        if (!canTransition(task.resolutionStatus(), normalizedStatus)) {
            throw new IllegalArgumentException(
                    "invalid dead letter status transition: " + task.resolutionStatus() + " -> " + normalizedStatus
            );
        }
        taskRepository.updateDeadLetterTaskStatus(deadLetterId, normalizedStatus);
    }

    @Transactional(readOnly = true)
    public List<DeadLetterTaskDto> deadLetterTasks() {
        return taskRepository.listDeadLetterTasks().stream()
                .map(it -> new DeadLetterTaskDto(
                        toIdString(it.id()),
                        toIdString(it.taskId()),
                        toIdString(it.docId()),
                        it.taskType(),
                        it.lastError(),
                        it.resolutionStatus(),
                        it.assignedTo(),
                        it.resolvedAt(),
                        it.createdAt(),
                        it.updatedAt()
                ))
                .toList();
    }

    private String toIdString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!List.of("open", "processing", "resolved", "closed").contains(normalized)) {
            throw new IllegalArgumentException("unsupported dead letter status: " + status);
        }
        return normalized;
    }

    private boolean canTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null) {
            return false;
        }
        String current = currentStatus.toLowerCase(Locale.ROOT);
        if (current.equals(targetStatus)) {
            return true;
        }
        return switch (current) {
            case "open" -> targetStatus.equals("processing");
            case "processing" -> targetStatus.equals("resolved") || targetStatus.equals("open");
            case "resolved" -> targetStatus.equals("closed") || targetStatus.equals("processing");
            case "closed" -> false;
            default -> false;
        };
    }
}
