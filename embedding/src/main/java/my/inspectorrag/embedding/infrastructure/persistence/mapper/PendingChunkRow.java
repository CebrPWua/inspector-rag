package my.inspectorrag.embedding.infrastructure.persistence.mapper;

public record PendingChunkRow(
        Long chunkId,
        String lawName,
        String chapterTitle,
        String sectionTitle,
        String articleNo,
        String content,
        Integer pageStart,
        Integer pageEnd,
        String versionNo,
        String status
) {
}
