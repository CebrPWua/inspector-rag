package my.inspectorrag.taskscheduling.application.service;

import my.inspectorrag.taskscheduling.domain.model.DeadLetterTask;
import my.inspectorrag.taskscheduling.domain.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskOpsServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Test
    void retryShouldSetTaskPendingDeadLetterProcessingAndResetParseStatus() {
        TaskOpsService service = new TaskOpsService(taskRepository);

        service.retry(100L);

        verify(taskRepository).retryTask(100L);
        verify(taskRepository).markDeadLetterProcessingByTaskId(100L);
        verify(taskRepository).markParseDocumentPendingForRetry(100L);
    }

    @Test
    void assignDeadLetterShouldUpdateAssignee() {
        TaskOpsService service = new TaskOpsService(taskRepository);
        when(taskRepository.findDeadLetterTask(7L)).thenReturn(Optional.of(sampleDeadLetter("open")));

        service.assignDeadLetter(7L, "alice");

        verify(taskRepository).assignDeadLetterTask(7L, "alice");
    }

    @Test
    void assignDeadLetterShouldFailWhenBlankAssignee() {
        TaskOpsService service = new TaskOpsService(taskRepository);

        assertThrows(IllegalArgumentException.class, () -> service.assignDeadLetter(7L, " "));
        verify(taskRepository, never()).assignDeadLetterTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateDeadLetterStatusShouldAllowValidTransition() {
        TaskOpsService service = new TaskOpsService(taskRepository);
        when(taskRepository.findDeadLetterTask(7L)).thenReturn(Optional.of(sampleDeadLetter("processing")));

        service.updateDeadLetterStatus(7L, "resolved");

        verify(taskRepository).updateDeadLetterTaskStatus(7L, "resolved");
    }

    @Test
    void updateDeadLetterStatusShouldRejectInvalidTransition() {
        TaskOpsService service = new TaskOpsService(taskRepository);
        when(taskRepository.findDeadLetterTask(7L)).thenReturn(Optional.of(sampleDeadLetter("open")));

        assertThrows(IllegalArgumentException.class, () -> service.updateDeadLetterStatus(7L, "closed"));
        verify(taskRepository, never()).updateDeadLetterTaskStatus(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deadLetterTasksShouldMapExtendedFields() {
        TaskOpsService service = new TaskOpsService(taskRepository);
        when(taskRepository.listDeadLetterTasks()).thenReturn(List.of(sampleDeadLetter("resolved")));

        var result = service.deadLetterTasks();

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).assignedTo());
        assertEquals("resolved", result.get(0).resolutionStatus());
    }

    private DeadLetterTask sampleDeadLetter(String status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DeadLetterTask(
                7L,
                101L,
                1001L,
                "embed",
                "error",
                status,
                "alice",
                null,
                now.minusHours(1),
                now
        );
    }
}
