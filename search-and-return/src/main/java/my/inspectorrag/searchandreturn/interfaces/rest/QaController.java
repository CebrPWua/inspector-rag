package my.inspectorrag.searchandreturn.interfaces.rest;

import jakarta.validation.Valid;
import my.inspectorrag.searchandreturn.application.service.QaApplicationService;
import my.inspectorrag.searchandreturn.application.service.RejectThresholdConfigApplicationService;
import my.inspectorrag.searchandreturn.interfaces.dto.ApiResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.AskRequest;
import my.inspectorrag.searchandreturn.interfaces.dto.AskResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.ConversationMessageResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.QaDetailResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.RejectThresholdConfigResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.UpdateRejectThresholdConfigRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaApplicationService qaApplicationService;
    private final RejectThresholdConfigApplicationService rejectThresholdConfigApplicationService;

    public QaController(
            QaApplicationService qaApplicationService,
            RejectThresholdConfigApplicationService rejectThresholdConfigApplicationService
    ) {
        this.qaApplicationService = qaApplicationService;
        this.rejectThresholdConfigApplicationService = rejectThresholdConfigApplicationService;
    }

    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.ok(qaApplicationService.ask(request.question(), request.conversationId(), request.filters()));
    }

    @GetMapping("/{qaId}")
    public ApiResponse<QaDetailResponse> detail(@PathVariable("qaId") Long qaId) {
        return ApiResponse.ok(qaApplicationService.getQa(qaId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<ConversationMessageResponse>> conversationMessages(@PathVariable("conversationId") Long conversationId) {
        return ApiResponse.ok(qaApplicationService.getConversationMessages(conversationId));
    }

    @GetMapping("/config/reject-thresholds")
    public ApiResponse<RejectThresholdConfigResponse> getRejectThresholdConfig() {
        return ApiResponse.ok(rejectThresholdConfigApplicationService.getCurrent());
    }

    @PutMapping("/config/reject-thresholds")
    public ApiResponse<RejectThresholdConfigResponse> updateRejectThresholdConfig(
            @Valid @RequestBody UpdateRejectThresholdConfigRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator
    ) {
        return ApiResponse.ok(rejectThresholdConfigApplicationService.update(request, operator));
    }
}
