package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.application.command.EmbedTaskCommand;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.repository.EmbeddingRepository;
import my.inspectorrag.embedding.domain.service.EmbeddingService;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import my.inspectorrag.embedding.interfaces.dto.EmbedTaskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmbeddingApplicationService {

    private final EmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;
    private final String modelName;
    private final String modelVersion;
    private final String provider;
    private final int dimension;

    public EmbeddingApplicationService(
            EmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService,
            VectorIndexService vectorIndexService,
            @Value("${inspector.embedding.model-name}") String modelName,
            @Value("${inspector.embedding.model-version}") String modelVersion,
            @Value("${inspector.embedding.provider}") String provider,
            @Value("${inspector.embedding.dimension}") int dimension
    ) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.vectorIndexService = vectorIndexService;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.provider = provider;
        this.dimension = dimension;
    }

    @Transactional
    public EmbedTaskResponse embed(EmbedTaskCommand command) {
        embeddingRepository.markTaskStatus(command.taskId(), "processing", null);

        try {
            OffsetDateTime now = OffsetDateTime.now();
            Long modelId = embeddingRepository.ensureActiveEmbeddingModel(modelName, modelVersion, dimension, provider, now);
            List<PendingChunk> chunks = embeddingRepository.findPendingChunks(command.docId(), 500);

            int processed = 0;
            for (PendingChunk chunk : chunks) {
                embeddingRepository.markChunkStatus(chunk.chunkId(), "processing");
                String inputText = "法规名称：" + nullSafe(chunk.lawName())
                        + "\n章节：" + nullSafe(chunk.chapterTitle()) + " / " + nullSafe(chunk.sectionTitle())
                        + "\n条款：" + nullSafe(chunk.articleNo())
                        + "\n正文：" + nullSafe(chunk.content());
                String vectorLiteral = embeddingService.toVectorLiteral(inputText, dimension);
                embeddingRepository.upsertChunkEmbedding(newId(), chunk.chunkId(), modelId, modelVersion, vectorLiteral, now);
                vectorIndexService.upsert(chunk);
                embeddingRepository.markChunkStatus(chunk.chunkId(), "success");
                processed++;
            }

            embeddingRepository.markTaskStatus(command.taskId(), "success", null);
            return new EmbedTaskResponse(command.taskId(), command.docId(), processed);
        } catch (Exception ex) {
            embeddingRepository.markTaskStatus(command.taskId(), "failed", ex.getMessage());
            throw ex;
        }
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
