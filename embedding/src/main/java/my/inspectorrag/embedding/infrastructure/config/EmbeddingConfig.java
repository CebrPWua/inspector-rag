package my.inspectorrag.embedding.infrastructure.config;

import my.inspectorrag.embedding.domain.service.EmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "inspector.embedding", name = "provider", havingValue = "mock", matchIfMissing = true)
    public EmbeddingService embeddingService() {
        return (text, dimension) -> {
            double[] vector = new double[dimension];
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                int idx = i % dimension;
                int value = bytes[i] & 0xFF;
                vector[idx] += (value / 255.0) * ((i % 2 == 0) ? 1.0 : -1.0);
            }

            double norm = 0.0;
            for (double v : vector) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm == 0.0) {
                norm = 1.0;
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                double normalized = vector[i] / norm;
                sb.append(String.format(Locale.ROOT, "%.8f", normalized));
            }
            sb.append(']');
            return sb.toString();
        };
    }
}
