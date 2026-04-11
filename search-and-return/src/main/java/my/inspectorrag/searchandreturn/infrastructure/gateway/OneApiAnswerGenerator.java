package my.inspectorrag.searchandreturn.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "inspector.ai", name = "provider", havingValue = "oneapi")
public class OneApiAnswerGenerator implements AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(OneApiAnswerGenerator.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;

    public OneApiAnswerGenerator(
            @Value("${inspector.ai.oneapi.base-url}") String baseUrl,
            @Value("${inspector.ai.oneapi.api-key}") String apiKey,
            @Value("${inspector.ai.oneapi.chat-model:gpt-5-mini}") String chatModel,
            @Value("${inspector.ai.oneapi.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${inspector.ai.oneapi.read-timeout-ms:120000}") int readTimeoutMs
    ) {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.chatModel = chatModel;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(normalizeHttpUri(baseUrl))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String generate(String normalizedQuestion, List<RecallCandidate> candidates) {
        StringBuilder evidence = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RecallCandidate c = candidates.get(i);
            evidence.append(i + 1)
                    .append(". 《")
                    .append(c.lawName())
                    .append("》")
                    .append(c.articleNo())
                    .append("：")
                    .append(c.content())
                    .append("\n");
        }

        String prompt = """
                你是法规问答助手。你必须仅基于给定证据回答，不能编造。
                问题：%s
                证据：
                %s
                按以下结构输出：
                问题描述：
                法规依据：
                风险说明：
                整改建议：
                """.formatted(normalizedQuestion, evidence);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", chatModel);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "你是严谨的法规合规助手。"),
                Map.of("role", "user", "content", prompt)
        ));

        long startedAt = System.currentTimeMillis();
        final String body;
        try {
            body = restClient.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startedAt;
            log.warn("oneapi chat request failed, model={}, elapsedMs={}", chatModel, elapsed, ex);
            throw ex;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("empty chat completion content");
            }
            long elapsed = System.currentTimeMillis() - startedAt;
            log.info("oneapi chat request succeeded, model={}, elapsedMs={}", chatModel, elapsed);
            return content;
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to parse oneapi chat completion response", ex);
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
