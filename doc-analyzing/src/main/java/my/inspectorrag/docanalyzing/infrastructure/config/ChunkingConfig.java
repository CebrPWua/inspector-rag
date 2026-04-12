package my.inspectorrag.docanalyzing.infrastructure.config;

import my.inspectorrag.docanalyzing.domain.model.ParsedChunk;
import my.inspectorrag.docanalyzing.domain.service.ChunkingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class ChunkingConfig {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("(第[一二三四五六七八九十百零0-9]+条)");

    @Bean
    public ChunkingService chunkingService() {
        return rawText -> {
            String normalized = rawText == null ? "" : rawText.replaceAll("\\r", "")
                    .replaceAll("[\\t ]+", " ")
                    .replaceAll("\\n{3,}", "\\n\\n")
                    .trim();

            if (normalized.isEmpty()) {
                return List.of();
            }

            List<ParsedChunk> chunks = new ArrayList<>();
            Matcher matcher = ARTICLE_PATTERN.matcher(normalized);
            List<Integer> starts = new ArrayList<>();
            while (matcher.find()) {
                starts.add(matcher.start());
            }

            if (starts.isEmpty()) {
                String content = truncate(normalized);
                chunks.add(new ParsedChunk("", "", "第1条", "", content, 1, sha256(content)));
                return chunks;
            }

            for (int i = 0; i < starts.size(); i++) {
                int start = starts.get(i);
                int end = i + 1 < starts.size() ? starts.get(i + 1) : normalized.length();
                String block = normalized.substring(start, end).trim();
                String articleNo = extractArticleNo(block);
                String content = truncate(block);
                chunks.add(new ParsedChunk("", "", articleNo, "", content, i + 1, sha256(content)));
            }

            return chunks;
        };
    }

    private static String truncate(String content) {
        if (content.length() <= 1400) {
            return content;
        }
        return content.substring(0, 1400);
    }

    private static String extractArticleNo(String block) {
        if (block == null || block.isBlank()) {
            return "第1条";
        }
        Matcher matcher = ARTICLE_PATTERN.matcher(block);
        if (matcher.find()) {
            String matched = matcher.group(1);
            if (matched != null && !matched.isBlank()) {
                return matched;
            }
        }
        String fallback = block.split("\\s", 2)[0].trim();
        if (fallback.isEmpty()) {
            return "第1条";
        }
        return fallback.length() <= 64 ? fallback : fallback.substring(0, 64);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
