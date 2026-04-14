package my.inspectorrag.embedding.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateEmbeddingProfileRequest(
        @NotBlank String profileKey,
        @NotBlank String provider,
        @NotBlank String modelName,
        @Min(1) @Max(16384) int dimension,
        @NotBlank String vectorType,
        @NotBlank String distanceMetric,
        @NotBlank String storageTable,
        boolean readEnabled,
        boolean writeEnabled,
        boolean defaultRead
) {
}
