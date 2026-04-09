package my.inspectorrag.filemanagement.interfaces.dto;

public record UploadFileResponse(Long docId, boolean duplicate, Long parseTaskId) {
}
