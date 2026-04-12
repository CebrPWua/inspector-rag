package my.inspectorrag.taskscheduling.interfaces.rest;

import jakarta.validation.Valid;
import my.inspectorrag.taskscheduling.interfaces.dto.AssignDeadLetterRequest;
import my.inspectorrag.taskscheduling.application.service.TaskOpsService;
import my.inspectorrag.taskscheduling.interfaces.dto.ApiResponse;
import my.inspectorrag.taskscheduling.interfaces.dto.DeadLetterTaskDto;
import my.inspectorrag.taskscheduling.interfaces.dto.UpdateDeadLetterStatusRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskOpsController {

    private final TaskOpsService taskOpsService;

    public TaskOpsController(TaskOpsService taskOpsService) {
        this.taskOpsService = taskOpsService;
    }

    @PostMapping("/retry/{taskId}")
    public ApiResponse<Void> retry(@PathVariable("taskId") Long taskId) {
        taskOpsService.retry(taskId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/dead-letter/{id}/assign")
    public ApiResponse<Void> assign(
            @PathVariable("id") Long id,
            @Valid @RequestBody AssignDeadLetterRequest request
    ) {
        taskOpsService.assignDeadLetter(id, request.assignedTo());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/dead-letter/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDeadLetterStatusRequest request
    ) {
        taskOpsService.updateDeadLetterStatus(id, request.status());
        return ApiResponse.ok(null);
    }

    @GetMapping("/dead-letter")
    public ApiResponse<List<DeadLetterTaskDto>> deadLetter() {
        return ApiResponse.ok(taskOpsService.deadLetterTasks());
    }
}
