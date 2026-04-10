package my.inspectorrag.searchandreturn.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.domain.service.MockEmbeddingService;
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
@ConditionalOnProperty(prefix = "inspector.ai", name = "provider", havingValue = "oneapi")
public class OneApiQueryEmbeddingService implements MockEmbeddingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String embeddingModelName;

    public OneApiQueryEmbeddingService(
            @Value("${inspector.ai.oneapi.base-url}") String baseUrl,
            @Value("${inspector.ai.oneapi.api-key}") String apiKey,
            @Value("${inspector.embedding.model-name}") String embeddingModelName
    ) {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.embeddingModelName = embeddingModelName;
        this.restClient = RestClient.builder()
                .baseUrl(normalizeHttpUri(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String toVectorLiteral(String text, int dimension) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", embeddingModelName);
        payload.put("input", text);
        String body = restClient.post()
                .uri("/v1/embeddings")
                .body(payload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode embedding = root.path("data").path(0).path("embedding");
            int size = Math.min(embedding.size(), dimension);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(String.format(Locale.ROOT, "%.8f", embedding.get(i).asDouble()));
            }
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
