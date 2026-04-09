package my.inspectorrag.taskscheduling.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inspector.internal-services")
public class InternalServiceProperties {

    private String docAnalyzingBaseUrl;
    private String embeddingBaseUrl;

    public String getDocAnalyzingBaseUrl() {
        return docAnalyzingBaseUrl;
    }

    public void setDocAnalyzingBaseUrl(String docAnalyzingBaseUrl) {
        this.docAnalyzingBaseUrl = docAnalyzingBaseUrl;
    }

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }
}
