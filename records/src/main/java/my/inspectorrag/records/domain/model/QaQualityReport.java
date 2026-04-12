package my.inspectorrag.records.domain.model;

import java.util.List;

public record QaQualityReport(
        long total,
        long success,
        long reject,
        long failed,
        Double avgElapsedMs,
        Double p95ElapsedMs,
        Double avgEvidenceCount,
        Double avgTop1FinalScore,
        List<RejectReasonStat> topRejectReasons
) {
}
