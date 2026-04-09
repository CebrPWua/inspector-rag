package my.inspectorrag.embedding.domain.service;

public interface EmbeddingService {
    String toVectorLiteral(String text, int dimension);
}
