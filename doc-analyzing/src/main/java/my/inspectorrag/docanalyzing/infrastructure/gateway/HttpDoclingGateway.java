package my.inspectorrag.docanalyzing.infrastructure.gateway;

import my.inspectorrag.docanalyzing.domain.service.DoclingGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "inspector.parsing.docling", name = "enabled", havingValue = "true")
public class HttpDoclingGateway implements DoclingGateway {

    private final RestClient restClient;
    private final String endpointPath;

    public HttpDoclingGateway(
            @Value("${inspector.parsing.docling.base-url}") String baseUrl,
            @Value("${inspector.parsing.docling.extract-path:/api/extract}") String endpointPath
    ) {
        this.endpointPath = endpointPath;
        this.restClient = RestClient.builder()
                .baseUrl(normalizeHttpUri(baseUrl))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String extractText(byte[] bytes, String fileName) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("fileName", fileName);
            payload.put("fileBase64", Base64.getEncoder().encodeToString(bytes));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpointPath)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return "";
            }
            Object text = response.get("text");
            return text == null ? "" : String.valueOf(text);
        } catch (Exception ex) {
            return "";
        }
    }

    private String normalizeHttpUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("docling base-url must not be blank when enabled");
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return "http://" + endpoint;
    }
}
