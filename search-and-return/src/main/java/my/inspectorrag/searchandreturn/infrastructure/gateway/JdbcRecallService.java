package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.MockEmbeddingService;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "inspector.retrieval", name = "provider", havingValue = "jdbc", matchIfMissing = true)
public class JdbcRecallService implements RecallService {

    private final MockEmbeddingService embeddingService;
    private final QaRepository qaRepository;

    public JdbcRecallService(MockEmbeddingService embeddingService, QaRepository qaRepository) {
        this.embeddingService = embeddingService;
        this.qaRepository = qaRepository;
    }

    @Override
    public List<RecallCandidate> recall(String normalizedQuestion, int topK) {
        String vectorLiteral = embeddingService.toVectorLiteral(normalizedQuestion, 1536);
        return qaRepository.vectorRecall(vectorLiteral, topK);
    }
}
