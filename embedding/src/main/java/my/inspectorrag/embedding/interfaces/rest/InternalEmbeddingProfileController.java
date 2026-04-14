package my.inspectorrag.embedding.interfaces.rest;

import jakarta.validation.Valid;
import my.inspectorrag.embedding.application.service.EmbeddingProfileAdminService;
import my.inspectorrag.embedding.interfaces.dto.ApiResponse;
import my.inspectorrag.embedding.interfaces.dto.CreateEmbeddingProfileRequest;
import my.inspectorrag.embedding.interfaces.dto.ToggleWriteProfileRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/embedding/profiles")
public class InternalEmbeddingProfileController {

    private final EmbeddingProfileAdminService profileAdminService;

    public InternalEmbeddingProfileController(EmbeddingProfileAdminService profileAdminService) {
        this.profileAdminService = profileAdminService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createProfile(@Valid @RequestBody CreateEmbeddingProfileRequest request) {
        profileAdminService.createProfile(request);
        return ApiResponse.ok(Map.of("profileKey", request.profileKey(), "created", true));
    }

    @PostMapping("/{profileKey}/activate-read")
    public ApiResponse<Map<String, Object>> activateRead(@PathVariable String profileKey) {
        profileAdminService.activateReadProfile(profileKey);
        return ApiResponse.ok(Map.of("profileKey", profileKey, "defaultRead", true));
    }

    @PostMapping("/{profileKey}/toggle-write")
    public ApiResponse<Map<String, Object>> toggleWrite(
            @PathVariable String profileKey,
            @Valid @RequestBody ToggleWriteProfileRequest request
    ) {
        profileAdminService.toggleWriteProfile(profileKey, request.enabled());
        return ApiResponse.ok(Map.of("profileKey", profileKey, "writeEnabled", request.enabled()));
    }
}
