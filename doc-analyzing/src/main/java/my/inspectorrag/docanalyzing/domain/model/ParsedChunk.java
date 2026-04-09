package my.inspectorrag.docanalyzing.domain.model;

public record ParsedChunk(
        String chapterTitle,
        String sectionTitle,
        String articleNo,
        String itemNo,
        String content,
        int chunkSeq,
        String contentHash
) {
}
