package my.inspectorrag.filemanagement.interfaces.dto;

public record UploadFileResponse(String docId, boolean duplicate, String parseTaskId) {
}
