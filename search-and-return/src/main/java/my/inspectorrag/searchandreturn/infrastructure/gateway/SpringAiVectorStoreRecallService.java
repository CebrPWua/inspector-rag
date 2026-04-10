package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "inspector.retrieval", name = "provider", havingValue = "springai")
public class SpringAiVectorStoreRecallService implements RecallService {

    private final VectorStore vectorStore;

    public SpringAiVectorStoreRecallService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RecallCandidate> recall(String normalizedQuestion, int topK) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(normalizedQuestion)
                        .topK(topK)
                        .similarityThresholdAll()
                        .build()
        );
        return docs.stream()
                .map(this::toCandidate)
                .toList();
    }

    private RecallCandidate toCandidate(Document doc) {
        var md = doc.getMetadata();
        return new RecallCandidate(
                toLong(md.get("chunkId"), doc.getId()),
                toString(md.get("lawName"), "未知法规"),
                toString(md.get("articleNo"), ""),
                doc.getText() == null ? "" : doc.getText(),
                doc.getScore(),
                toInteger(md.get("pageStart")),
                toInteger(md.get("pageEnd")),
                toString(md.get("versionNo"), "")
        );
    }

    private Long toLong(Object value, String fallback) {
        if (value == null) {
            return parseLongOrNull(fallback);
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return parseLongOrNull(String.valueOf(value));
    }

    private Long parseLongOrNull(String text) {
        try {
            return Long.parseLong(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private String toString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }
}
