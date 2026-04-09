package my.inspectorrag.records.interfaces.rest;

import my.inspectorrag.records.application.service.RecordsApplicationService;
import my.inspectorrag.records.interfaces.dto.ApiResponse;
import my.inspectorrag.records.interfaces.dto.QaRecordItemDto;
import my.inspectorrag.records.interfaces.dto.QaReplayDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/records")
public class RecordsController {

    private final RecordsApplicationService recordsApplicationService;

    public RecordsController(RecordsApplicationService recordsApplicationService) {
        this.recordsApplicationService = recordsApplicationService;
    }

    @GetMapping("/qa")
    public ApiResponse<List<QaRecordItemDto>> listQa(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.ok(recordsApplicationService.listQa(limit));
    }

    @GetMapping("/qa/{qaId}/replay")
    public ApiResponse<QaReplayDto> replay(@PathVariable("qaId") Long qaId) {
        return ApiResponse.ok(recordsApplicationService.replay(qaId));
    }
}
