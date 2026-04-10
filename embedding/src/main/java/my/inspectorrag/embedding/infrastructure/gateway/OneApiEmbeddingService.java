package my.inspectorrag.embedding.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.embedding.domain.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "inspector.embedding", name = "provider", havingValue = "oneapi")
public class OneApiEmbeddingService implements EmbeddingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String modelName;

    public OneApiEmbeddingService(
            @Value("${inspector.embedding.oneapi.base-url}") String baseUrl,
            @Value("${inspector.embedding.oneapi.api-key}") String apiKey,
            @Value("${inspector.embedding.model-name}") String modelName
    ) {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.modelName = modelName;
        this.restClient = RestClient.builder()
                .baseUrl(normalizeHttpUri(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String toVectorLiteral(String text, int dimension) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("input", text);
        String body = restClient.post()
                .uri("/v1/embeddings")
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode embedding = root.path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalArgumentException("embedding response is empty");
            }
            int size = Math.min(dimension, embedding.size());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(String.format(Locale.ROOT, "%.8f", embedding.get(i).asDouble()));
            }
            // When upstream vector is shorter than expected dimension, pad with zeros.
            for (int i = size; i < dimension; i++) {
                sb.append(",0.00000000");
            }
            sb.append(']');
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to parse oneapi embedding response", ex);
        }
    }

    private String normalizeHttpUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("oneapi base-url must not be blank");
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return "http://" + endpoint;
    }
}
