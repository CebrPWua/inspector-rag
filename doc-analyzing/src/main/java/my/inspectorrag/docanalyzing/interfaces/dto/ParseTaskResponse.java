package my.inspectorrag.docanalyzing.interfaces.dto;

public record ParseTaskResponse(Long taskId, Long docId, int chunkCount, Long embedTaskId) {
}
