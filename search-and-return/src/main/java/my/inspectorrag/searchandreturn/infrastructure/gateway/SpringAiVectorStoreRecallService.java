package my.inspectorrag.searchandreturn.infrastructure.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SpringAiVectorStoreRecallService implements RecallService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiVectorStoreRecallService.class);
    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final String routerMode;
    private final int canaryPercent;
    private final String forcedProfile;
    private final int quantizedRerankMultiplier;

    public SpringAiVectorStoreRecallService(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${inspector.embedding.router.mode:default}") String routerMode,
            @Value("${inspector.embedding.router.canary-percent:0}") int canaryPercent,
            @Value("${inspector.embedding.router.forced-profile:}") String forcedProfile,
            @Value("${inspector.embedding.router.quantized-rerank-multiplier:8}") int quantizedRerankMultiplier
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
        this.routerMode = routerMode == null ? "default" : routerMode.trim().toLowerCase(Locale.ROOT);
        this.canaryPercent = Math.max(0, Math.min(canaryPercent, 100));
        this.forcedProfile = forcedProfile == null ? "" : forcedProfile.trim();
        this.quantizedRerankMultiplier = Math.max(2, quantizedRerankMultiplier);
    }

    @Override
    public List<RecallCandidate> recall(String normalizedQuestion, int topK, QaFilters filters) {
        return recall(normalizedQuestion, topK, filters, null);
    }

    @Override
    public List<RecallCandidate> recall(String normalizedQuestion, int topK, QaFilters filters, String routeKey) {
        try {
            EmbeddingProfile profile = resolveProfile(routeKey);
            if (profile == null) {
                log.warn("vector recall skipped because no readable embedding profile is available");
                return List.of();
            }
            float[] rawVector = embeddingModel.embed(normalizedQuestion);
            if (rawVector == null || rawVector.length == 0) {
                log.warn("vector recall skipped because embedding model returned empty vector");
                return List.of();
            }
            if (rawVector.length < profile.dimension()) {
                log.warn("vector recall skipped because dimension mismatch. profile={}, expected={}, actual={}",
                        profile.profileKey(), profile.dimension(), rawVector.length);
                return List.of();
            }
            float[] vector = projectVector(rawVector, profile.dimension());
            String table = safeTable(profile.storageTable());
            String castType = castType(profile.vectorType(), profile.dimension());
            String vectorLiteral = toVectorLiteral(vector);
            if (useQuantizedTwoStage(profile)) {
                return recallWithQuantizedTwoStage(table, castType, vectorLiteral, profile.dimension(), topK);
            }
            String sql = """
                    select id, content, metadata, 1 - (embedding <=> cast(? as %s)) as score
                      from %s
                     order by embedding <=> cast(? as %s)
                     limit ?
                    """.formatted(castType, table, castType);
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> toCandidate(rs),
                    vectorLiteral,
                    vectorLiteral,
                    topK
            );
        } catch (RuntimeException ex) {
            // Degrade to keyword-only recall when vector recall dependency is unstable.
            log.warn("vector recall failed, fallback to keyword recall only. question={}, topK={}, error={}",
                    normalizedQuestion, topK, ex.getMessage());
            return List.of();
        }
    }

    private List<RecallCandidate> recallWithQuantizedTwoStage(
            String table,
            String castType,
            String vectorLiteral,
            int dimension,
            int topK
    ) {
        int rerankTopK = Math.max(topK, topK * quantizedRerankMultiplier);
        String sql = """
                with coarse as (
                    select id, content, metadata, embedding
                      from %s
                     order by (binary_quantize(embedding)::bit(%d)) <~> (binary_quantize(cast(? as %s))::bit(%d))
                     limit ?
                )
                select id, content, metadata, 1 - (embedding <=> cast(? as %s)) as score
                  from coarse
                 order by embedding <=> cast(? as %s)
                 limit ?
                """.formatted(table, dimension, castType, dimension, castType, castType);
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> toCandidate(rs),
                vectorLiteral,
                rerankTopK,
                vectorLiteral,
                vectorLiteral,
                topK
        );
    }

    @Override
    public String resolveProfileKey(String routeKey) {
        EmbeddingProfile profile = resolveProfile(routeKey);
        return profile == null ? null : profile.profileKey();
    }

    private RecallCandidate toCandidate(ResultSet rs) throws SQLException {
        Map<String, Object> md = parseMetadata(rs.getObject("metadata"));
        return new RecallCandidate(
                toLong(md.get("chunkId"), rs.getString("id")),
                toString(md.get("lawName"), "未知法规"),
                toString(md.get("articleNo"), ""),
                toString(md.get("content"), rs.getString("content") == null ? "" : rs.getString("content")),
                toDouble(rs.getObject("score")),
                toInteger(md.get("pageStart")),
                toInteger(md.get("pageEnd")),
                toString(md.get("versionNo"), "")
        );
    }

    private EmbeddingProfile resolveProfile(String routeKey) {
        List<EmbeddingProfile> readableProfiles = loadReadableProfiles();
        if (readableProfiles.isEmpty()) {
            return null;
        }
        EmbeddingProfile defaultProfile = readableProfiles.stream()
                .filter(EmbeddingProfile::isDefaultRead)
                .findFirst()
                .orElseGet(() -> readableProfiles.getFirst());
        if ("forced".equals(routerMode) && !forcedProfile.isEmpty()) {
            return readableProfiles.stream()
                    .filter(p -> forcedProfile.equals(p.profileKey()))
                    .findFirst()
                    .orElse(defaultProfile);
        }
        if ("canary".equals(routerMode)) {
            List<EmbeddingProfile> canaryProfiles = readableProfiles.stream()
                    .filter(p -> !p.isDefaultRead())
                    .sorted(Comparator.comparing(EmbeddingProfile::profileKey))
                    .toList();
            if (!canaryProfiles.isEmpty()) {
                int bucket = Math.floorMod((routeKey == null ? "" : routeKey).hashCode(), 100);
                if (bucket < canaryPercent) {
                    return canaryProfiles.getFirst();
                }
            }
        }
        return defaultProfile;
    }

    private List<EmbeddingProfile> loadReadableProfiles() {
        String sql = """
                select profile_key, model_name, dimension, vector_type, storage_table, is_default_read
                  from indexing.embedding_profile
                 where read_enabled = true
                 order by profile_key
                """;
        return jdbcTemplate.query(sql, this::mapProfile);
    }

    private EmbeddingProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new EmbeddingProfile(
                rs.getString("profile_key"),
                rs.getString("model_name"),
                rs.getInt("dimension"),
                rs.getString("vector_type"),
                rs.getString("storage_table"),
                rs.getBoolean("is_default_read")
        );
    }

    private String safeTable(String storageTable) {
        if (storageTable == null || !QUALIFIED_IDENTIFIER.matcher(storageTable).matches()) {
            throw new IllegalArgumentException("invalid storage table identifier: " + storageTable);
        }
        return storageTable;
    }

    private String castType(String vectorType, int dimension) {
        String normalized = Optional.ofNullable(vectorType).orElse("").trim().toLowerCase(Locale.ROOT);
        if (!"vector".equals(normalized) && !"halfvec".equals(normalized)) {
            throw new IllegalArgumentException("unsupported vector type: " + vectorType);
        }
        return normalized + "(" + dimension + ")";
    }

    private boolean useQuantizedTwoStage(EmbeddingProfile profile) {
        return "halfvec".equals(profile.vectorType()) && profile.dimension() > 4000;
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            float value = vector[i];
            sb.append(Float.isFinite(value) ? value : 0.0f);
        }
        sb.append(']');
        return sb.toString();
    }

    private float[] projectVector(float[] rawVector, int targetDimension) {
        if (rawVector.length == targetDimension) {
            return rawVector;
        }
        float[] projected = new float[targetDimension];
        System.arraycopy(rawVector, 0, projected, 0, targetDimension);
        return projected;
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

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
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

    private Map<String, Object> parseMetadata(Object metadataObj) {
        if (metadataObj == null) {
            return Map.of();
        }
        String json = String.valueOf(metadataObj);
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("failed to parse vector metadata json, raw={}", json);
            return Map.of();
        }
    }

    private record EmbeddingProfile(
            String profileKey,
            String modelName,
            int dimension,
            String vectorType,
            String storageTable,
            boolean isDefaultRead
    ) {
    }
}
