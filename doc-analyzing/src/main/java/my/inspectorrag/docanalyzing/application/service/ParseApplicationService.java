package my.inspectorrag.docanalyzing.application.service;

import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import my.inspectorrag.docanalyzing.interfaces.dto.ParseTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ParseApplicationService {

    private final ParseRepository parseRepository;
    private final ChunkingService chunkingService;

    public ParseApplicationService(ParseRepository parseRepository, ChunkingService chunkingService) {
        this.parseRepository = parseRepository;
        this.chunkingService = chunkingService;
    }

    @Transactional
    public ParseTaskResponse parse(ParseTaskCommand command) {
        parseRepository.markTaskStatus(command.taskId(), "processing", null);
        parseRepository.updateParseStatus(command.docId(), "processing");

        try {
            String storagePath = parseRepository.findPrimaryStoragePath(command.docId())
                    .orElseThrow(() -> new IllegalArgumentException("primary file not found for docId=" + command.docId()));

            String rawText = loadText(storagePath);
            List<ParsedChunk> chunks = chunkingService.splitToChunks(rawText);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("parsed chunk is empty");
            }

            parseRepository.deleteExistingChunks(command.docId());
            OffsetDateTime now = OffsetDateTime.now();
            for (ParsedChunk chunk : chunks) {
                Long chunkId = newId();
                parseRepository.insertChunk(chunkId, command.docId(), chunk, now);
                parseRepository.insertChunkTag(newId(), chunkId, "industry", "建筑施工", now);
            }

            Long embedTaskId = parseRepository.createImportTask(newId(), command.docId(), "embed", now);
            parseRepository.updateParseStatus(command.docId(), "success");
            parseRepository.markTaskStatus(command.taskId(), "success", null);
            return new ParseTaskResponse(command.taskId(), command.docId(), chunks.size(), embedTaskId);
        } catch (Exception ex) {
            parseRepository.updateParseStatus(command.docId(), "failed");
            parseRepository.markTaskStatus(command.taskId(), "failed", ex.getMessage());
            throw ex;
        }
    }

    private String loadText(String storagePath) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(storagePath));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read source file: " + storagePath, ex);
        }
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
