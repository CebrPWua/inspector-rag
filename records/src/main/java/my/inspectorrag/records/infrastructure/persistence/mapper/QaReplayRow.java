package my.inspectorrag.records.infrastructure.persistence.mapper;

public record QaReplayRow(
        Long qaId,
        String question,
        String normalizedQuestion,
        String answer
) {
}
