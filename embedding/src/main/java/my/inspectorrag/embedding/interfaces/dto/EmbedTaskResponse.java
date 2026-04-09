package my.inspectorrag.embedding.interfaces.dto;

public record EmbedTaskResponse(Long taskId, Long docId, int processedChunks) {
}
