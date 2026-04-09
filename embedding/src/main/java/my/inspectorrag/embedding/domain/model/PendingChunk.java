package my.inspectorrag.embedding.domain.model;

public record PendingChunk(
        Long chunkId,
        String lawName,
        String chapterTitle,
        String sectionTitle,
        String articleNo,
        String content
) {
}
