package my.inspectorrag.records.interfaces.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record QaQualityReportDto(
        OffsetDateTime from,
        OffsetDateTime to,
        long total,
        long success,
        long reject,
        long failed,
        double rejectRate,
        double failedRate,
        Double avgElapsedMs,
        Double p95ElapsedMs,
        Double avgEvidenceCount,
        Double avgTop1FinalScore,
        List<RejectReasonStatDto> topRejectReasons
) {
}
