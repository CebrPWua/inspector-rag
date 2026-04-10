package my.inspectorrag.docanalyzing.application.service;

import my.inspectorrag.docanalyzing.application.command.ParseTaskCommand;
import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.repository.ParseRepository;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import my.inspectorrag.docanalyzing.domain.service.DoclingGateway;
import my.inspectorrag.docanalyzing.domain.service.SourceDocumentGateway;
import my.inspectorrag.docanalyzing.interfaces.dto.ParseTaskResponse;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ParseApplicationService {

    private static final Pattern DOCX_TEXT_PATTERN = Pattern.compile("<w:t[^>]*>(.*?)</w:t>");
    private static final Tika TIKA = new Tika();

    private final ParseRepository parseRepository;
    private final ChunkingService chunkingService;
    private final SourceDocumentGateway sourceDocumentGateway;
    private final DoclingGateway doclingGateway;

    public ParseApplicationService(
            ParseRepository parseRepository,
            ChunkingService chunkingService,
            SourceDocumentGateway sourceDocumentGateway,
            DoclingGateway doclingGateway
    ) {
        this.parseRepository = parseRepository;
        this.chunkingService = chunkingService;
        this.sourceDocumentGateway = sourceDocumentGateway;
        this.doclingGateway = doclingGateway;
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
            byte[] bytes = sourceDocumentGateway.read(storagePath);
            String fileName = extractFileName(storagePath).toLowerCase();
            if (fileName.endsWith(".docx")) {
                return sanitizeForDb(extractDocxText(bytes));
            }
            if (fileName.endsWith(".pdf") || fileName.endsWith(".doc") || fileName.endsWith(".docx") || fileName.endsWith(".txt")) {
                try {
                    String tikaText = TIKA.parseToString(new ByteArrayInputStream(bytes));
                    if (tikaText != null && !tikaText.isBlank()) {
                        return sanitizeForDb(tikaText);
                    }
                } catch (Exception ignored) {
                    // fall through to plain text reading as a safe fallback
                }
            }
            if (fileName.endsWith(".pdf")) {
                String doclingText = doclingGateway.extractText(bytes, fileName);
                if (doclingText != null && !doclingText.isBlank()) {
                    return sanitizeForDb(doclingText);
                }
            }
            return sanitizeForDb(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read source file: " + storagePath, ex);
        }
    }

    private String extractDocxText(byte[] docxBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                return extractTextFromDocxXml(xml);
            }
            throw new IllegalArgumentException("invalid docx content: missing word/document.xml");
        }
    }

    private String extractTextFromDocxXml(String xml) {
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

    private String extractFileName(String storagePath) {
        int idx = storagePath.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < storagePath.length()) {
            return storagePath.substring(idx + 1);
        }
        return storagePath;
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
