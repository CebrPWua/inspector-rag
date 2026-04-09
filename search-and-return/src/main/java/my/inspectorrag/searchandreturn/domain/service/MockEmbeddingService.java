package my.inspectorrag.searchandreturn.domain.service;

public interface MockEmbeddingService {
    String toVectorLiteral(String text, int dimension);
}
