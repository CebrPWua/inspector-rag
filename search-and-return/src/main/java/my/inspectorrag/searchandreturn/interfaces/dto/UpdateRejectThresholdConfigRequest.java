package my.inspectorrag.searchandreturn.interfaces.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRejectThresholdConfigRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minTop1Score,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minTop1ScoreVectorOnly,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minTopGap,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double minConfidentScore,
        @NotNull @Min(1) Integer minEvidenceCount
) {
}
