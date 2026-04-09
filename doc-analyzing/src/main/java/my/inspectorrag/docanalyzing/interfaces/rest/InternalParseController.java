package my.inspectorrag.docanalyzing.interfaces.rest;

import jakarta.validation.Valid;
import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.application.service.ParseApplicationService;
import my.inspectorrag.docanalyzing.interfaces.dto.ApiResponse;
import my.inspectorrag.docanalyzing.interfaces.dto.ParseTaskRequest;
import my.inspectorrag.docanalyzing.interfaces.dto.ParseTaskResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tasks")
public class InternalParseController {

    private final ParseApplicationService parseApplicationService;

    public InternalParseController(ParseApplicationService parseApplicationService) {
        this.parseApplicationService = parseApplicationService;
    }

    @PostMapping("/parse")
    public ApiResponse<ParseTaskResponse> parse(@Valid @RequestBody ParseTaskRequest request) {
        ParseTaskResponse response = parseApplicationService.parse(new ParseTaskCommand(request.taskId(), request.docId()));
        return ApiResponse.ok(response);
    }
}
