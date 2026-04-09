package my.inspectorrag.embedding.interfaces.rest;

import jakarta.validation.Valid;
import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.application.service.EmbeddingApplicationService;
import my.inspectorrag.embedding.interfaces.dto.ApiResponse;
import my.inspectorrag.embedding.interfaces.dto.EmbedTaskRequest;
import my.inspectorrag.embedding.interfaces.dto.EmbedTaskResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tasks")
public class InternalEmbeddingController {

    private final EmbeddingApplicationService embeddingApplicationService;

    public InternalEmbeddingController(EmbeddingApplicationService embeddingApplicationService) {
        this.embeddingApplicationService = embeddingApplicationService;
    }

    @PostMapping("/embed")
    public ApiResponse<EmbedTaskResponse> embed(@Valid @RequestBody EmbedTaskRequest request) {
        EmbedTaskResponse response = embeddingApplicationService.embed(new EmbedTaskCommand(request.taskId(), request.docId()));
        return ApiResponse.ok(response);
    }
}
