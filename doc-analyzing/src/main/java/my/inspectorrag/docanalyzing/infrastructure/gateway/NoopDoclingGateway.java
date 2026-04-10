package my.inspectorrag.docanalyzing.infrastructure.gateway;

import my.inspectorrag.docanalyzing.domain.service.DoclingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "inspector.parsing.docling", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDoclingGateway implements DoclingGateway {

    @Override
    public String extractText(byte[] bytes, String fileName) {
        return "";
    }
}
