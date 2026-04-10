package my.inspectorrag.docanalyzing.domain.service;

public interface SourceDocumentGateway {

    byte[] read(String storagePath);
}
