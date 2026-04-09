package my.inspectorrag.taskscheduling.application.service;

import my.inspectorrag.taskscheduling.domain.model.ImportTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import my.inspectorrag.taskscheduling.infrastructure.config.TaskWorkerProperties;
import my.inspectorrag.taskscheduling.infrastructure.http.TaskDispatcherClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskWorkerServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskDispatcherClient taskDispatcherClient;

    @Test
    void pollAndRunShouldDispatchAndMarkSuccess() {
        TaskWorkerProperties properties = new TaskWorkerProperties();
        properties.setWorkerEnabled(true);
        properties.setClaimLimit(10);

        TaskWorkerService service = new TaskWorkerService(taskRepository, taskDispatcherClient, properties);

        ImportTask task = new ImportTask(1L, 100L, "parse", "processing", 0, 3);
        when(taskRepository.claimTasks(10)).thenReturn(List.of(task));

        service.pollAndRun();

        verify(taskDispatcherClient).dispatch(task);
        verify(taskRepository).markTaskSuccess(1L);
    }

    @Test
    void processTaskShouldRetryWhenBelowMaxRetry() {
        TaskWorkerProperties properties = new TaskWorkerProperties();
        TaskWorkerService service = new TaskWorkerService(taskRepository, taskDispatcherClient, properties);

        ImportTask task = new ImportTask(2L, 101L, "parse", "processing", 0, 3);
        doThrow(new IllegalStateException("dispatch fail")).when(taskDispatcherClient).dispatch(task);

        service.processTask(task);

        verify(taskRepository).insertRetryLog(anyLong(), eq(2L), eq(1), contains("dispatch fail"), any());
        verify(taskRepository).incrementRetry(2L, "dispatch fail");
        verify(taskRepository, never()).moveToDeadLetter(anyLong(), any(), anyString(), any());
    }

    @Test
    void processTaskShouldMoveToDeadLetterWhenRetryExceeded() {
        TaskWorkerProperties properties = new TaskWorkerProperties();
        TaskWorkerService service = new TaskWorkerService(taskRepository, taskDispatcherClient, properties);

        ImportTask task = new ImportTask(3L, 102L, "embed", "processing", 3, 3);
        doThrow(new IllegalStateException("dispatch fail"))
                .when(taskDispatcherClient).dispatch(task);

        service.processTask(task);

        verify(taskRepository).markTaskFailed(3L, "dispatch fail");
        verify(taskRepository).moveToDeadLetter(anyLong(), eq(task), eq("dispatch fail"), any());
    }
}
