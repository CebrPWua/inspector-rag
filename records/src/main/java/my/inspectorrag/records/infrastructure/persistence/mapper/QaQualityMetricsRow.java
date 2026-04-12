package my.inspectorrag.records.infrastructure.persistence.mapper;

public record QaQualityMetricsRow(
        Long total,
        Long success,
        Long reject,
        Long failed,
        Double avgElapsedMs,
        Double p95ElapsedMs,
        Double avgEvidenceCount,
        Double avgTop1FinalScore
) {
}
