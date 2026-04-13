package my.inspectorrag.embedding.infrastructure.gateway;

import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiPgVectorIndexService implements VectorIndexService {

    private final VectorStore vectorStore;

    public SpringAiPgVectorIndexService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void upsert(PendingChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId", chunk.chunkId());
        metadata.put("lawName", nullSafe(chunk.lawName()));
        metadata.put("articleNo", nullSafe(chunk.articleNo()));
        metadata.put("chapterTitle", nullSafe(chunk.chapterTitle()));
        metadata.put("sectionTitle", nullSafe(chunk.sectionTitle()));
        metadata.put("versionNo", nullSafe(chunk.versionNo()));
        metadata.put("status", nullSafe(chunk.status()));
        metadata.put("content", nullSafe(chunk.content()));
        if (chunk.pageStart() != null) {
            metadata.put("pageStart", chunk.pageStart());
        }
        if (chunk.pageEnd() != null) {
            metadata.put("pageEnd", chunk.pageEnd());
        }

        Document doc = Document.builder()
                .id(String.valueOf(chunk.chunkId()))
                .text(buildEmbeddingInput(chunk))
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(doc));
    }

    private String buildEmbeddingInput(PendingChunk chunk) {
        return "法规名称：" + nullSafe(chunk.lawName())
                + "\n章节：" + nullSafe(chunk.chapterTitle()) + " / " + nullSafe(chunk.sectionTitle())
                + "\n条款：" + nullSafe(chunk.articleNo())
                + "\n正文：" + nullSafe(chunk.content());
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }
}
