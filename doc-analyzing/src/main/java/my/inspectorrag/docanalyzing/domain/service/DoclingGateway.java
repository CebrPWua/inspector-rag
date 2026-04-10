package my.inspectorrag.docanalyzing.domain.service;

public interface DoclingGateway {

    String extractText(byte[] bytes, String fileName);
}
