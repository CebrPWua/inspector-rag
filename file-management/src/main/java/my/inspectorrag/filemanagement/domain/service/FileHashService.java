package my.inspectorrag.filemanagement.domain.service;

public interface FileHashService {
    String sha256(byte[] bytes);
}
