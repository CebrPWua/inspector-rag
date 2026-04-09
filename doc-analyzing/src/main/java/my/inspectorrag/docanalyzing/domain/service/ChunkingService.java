package my.inspectorrag.docanalyzing.domain.service;

import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;

import java.util.List;

public interface ChunkingService {
    List<ParsedChunk> splitToChunks(String rawText);
}
