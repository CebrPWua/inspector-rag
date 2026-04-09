package my.inspectorrag.docanalyzing.application.service;

import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import my.inspectorrag.docanalyzing.interfaces.dto.ParseTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class ParseApplicationService {

    private static final Pattern DOCX_TEXT_PATTERN = Pattern.compile("<w:t[^>]*>(.*?)</w:t>");

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
            Path path = Path.of(storagePath);
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".docx")) {
                return sanitizeForDb(extractDocxText(path));
            }

            byte[] bytes = Files.readAllBytes(path);
            return sanitizeForDb(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read source file: " + storagePath, ex);
        }
    }

    private String extractDocxText(Path docxPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(docxPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                throw new IllegalArgumentException("invalid docx content: missing word/document.xml");
            }

            String xml;
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            StringBuilder sb = new StringBuilder();
            Matcher matcher = DOCX_TEXT_PATTERN.matcher(xml);
            while (matcher.find()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(unescapeXml(matcher.group(1)));
            }

            String text = sb.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }

            return unescapeXml(xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
        }
    }

    private String sanitizeForDb(String text) {
        if (text == null) {
            return "";
        }
        // PostgreSQL text does not allow NUL bytes.
        return text.replace("\u0000", "")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t\\r]]", " ")
                .trim();
    }

    private String unescapeXml(String input) {
        return input.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
