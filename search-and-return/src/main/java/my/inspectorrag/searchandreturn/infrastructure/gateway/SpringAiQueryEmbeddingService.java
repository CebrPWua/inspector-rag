package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.service.MockEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "inspector.ai", name = "provider", havingValue = "springai")
public class SpringAiQueryEmbeddingService implements MockEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public SpringAiQueryEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String toVectorLiteral(String text, int dimension) {
        float[] vector = embeddingModel.embed(text);
        int size = Math.min(vector.length, dimension);
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
