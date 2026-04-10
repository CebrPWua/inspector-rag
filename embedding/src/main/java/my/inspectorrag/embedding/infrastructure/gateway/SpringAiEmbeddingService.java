package my.inspectorrag.embedding.infrastructure.gateway;

import my.inspectorrag.embedding.domain.service.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "inspector.embedding", name = "provider", havingValue = "springai")
public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String toVectorLiteral(String text, int dimension) {
        float[] vector = embeddingModel.embed(text);
        int size = Math.min(dimension, vector.length);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        for (int i = size; i < dimension; i++) {
            sb.append(",0.00000000");
        }
        sb.append(']');
        return sb.toString();
    }
}
