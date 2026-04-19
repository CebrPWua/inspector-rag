package my.inspectorrag.taskscheduling.application.service;

import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import my.inspectorrag.taskscheduling.infrastructure.config.TaskWorkerProperties;
import my.inspectorrag.taskscheduling.infrastructure.http.TaskDispatcherClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TaskWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkerService.class);

    private final TaskRepository taskRepository;
    private final TaskDispatcherClient taskDispatcherClient;
    private final TaskWorkerProperties properties;

    public TaskWorkerService(
            TaskRepository taskRepository,
            TaskDispatcherClient taskDispatcherClient,
            TaskWorkerProperties properties
    ) {
        this.taskRepository = taskRepository;
        this.taskDispatcherClient = taskDispatcherClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${TASK_WORKER_DELAY_MS:3000}")
    public void pollAndRun() {
        if (!properties.isWorkerEnabled()) {
            return;
        }

        List<ImportTask> tasks = taskRepository.claimTasks(properties.getClaimLimit());
        for (ImportTask task : tasks) {
            processTask(task);
        }
    }

    public void processTask(ImportTask task) {
        try {
            taskDispatcherClient.dispatch(task);
            taskRepository.markTaskSuccess(task.id());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "unknown dispatch error" : ex.getMessage();
            int nextRetry = task.retryCount() + 1;
            OffsetDateTime now = OffsetDateTime.now();
            taskRepository.insertRetryLog(newId(), task.id(), nextRetry, message, now);
            if (nextRetry > task.maxRetry()) {
                taskRepository.markTaskFailed(task.id(), message);
                taskRepository.moveToDeadLetter(newId(), task, message, now);
                if ("parse".equals(task.taskType())) {
                    taskRepository.markParseDocumentFailedForDeadLetter(task.id());
                }
            } else {
                taskRepository.incrementRetry(task.id(), message);
            }
            log.error("task dispatch failed taskId={}, retry={}, maxRetry={}", task.id(), nextRetry, task.maxRetry(), ex);
        }
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
