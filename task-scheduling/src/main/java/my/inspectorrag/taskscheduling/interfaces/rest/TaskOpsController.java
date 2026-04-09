package my.inspectorrag.taskscheduling.interfaces.rest;

import my.inspectorrag.taskscheduling.application.service.TaskOpsService;
import my.inspectorrag.taskscheduling.interfaces.dto.ApiResponse;
import my.inspectorrag.taskscheduling.interfaces.dto.DeadLetterTaskDto;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/dead-letter")
    public ApiResponse<List<DeadLetterTaskDto>> deadLetter() {
        return ApiResponse.ok(taskOpsService.deadLetterTasks());
    }
}
