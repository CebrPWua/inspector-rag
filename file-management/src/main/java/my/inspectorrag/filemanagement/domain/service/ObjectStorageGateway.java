package my.inspectorrag.filemanagement.domain.service;

public interface ObjectStorageGateway {

    String save(Long docId, String originalFilename, byte[] content);
}

