package my.inspectorrag.embedding.domain.model;

import my.inspectorrag.embedding.domain.model.value.ChunkId;
import my.inspectorrag.embedding.domain.model.value.EmbeddingText;

import java.util.Objects;

public record PendingChunk(
        ChunkId chunkId,
        String lawName,
        String chapterTitle,
        String sectionTitle,
        String articleNo,
        String content,
        Integer pageStart,
        Integer pageEnd,
        String versionNo,
        String status
) {

    public PendingChunk {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    public EmbeddingText toEmbeddingText() {
        String text = "法规名称：" + nullSafe(lawName)
                + "\n章节：" + nullSafe(chapterTitle) + " / " + nullSafe(sectionTitle)
                + "\n条款：" + nullSafe(articleNo)
                + "\n正文：" + nullSafe(content);
        return new EmbeddingText(text);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
