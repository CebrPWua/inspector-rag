package my.inspectorrag.embedding.infrastructure.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import my.inspectorrag.embedding.domain.model.PendingChunk;
import my.inspectorrag.embedding.domain.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SpringAiPgVectorIndexService implements VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiPgVectorIndexService.class);
    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public SpringAiPgVectorIndexService(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void upsert(PendingChunk chunk) {
        String embeddingInput = chunk.toEmbeddingText().value();
        float[] rawVector = embeddingModel.embed(embeddingInput);
        if (rawVector == null || rawVector.length == 0) {
            throw new IllegalStateException("embedding model returned empty vector");
        }

        List<WriteProfile> profiles = loadWriteProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("no write-enabled embedding profile found");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId", chunk.chunkId().value());
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

        String metadataJson = toJson(metadata);
        for (WriteProfile profile : profiles) {
            float[] vector = projectVector(rawVector, profile.dimension());
            String table = safeTable(profile.storageTable());
            String castType = castType(profile.vectorType(), profile.dimension());
            String vectorLiteral = toVectorLiteral(vector);
            String upsertSql = """
                    insert into %s (id, content, metadata, embedding, created_at, updated_at)
                    values (?, ?, cast(? as jsonb), cast(? as %s), now(), now())
                    on conflict (id) do update
                    set content = excluded.content,
                        metadata = excluded.metadata,
                        embedding = excluded.embedding,
                        updated_at = now()
                    """.formatted(table, castType);
            jdbcTemplate.update(upsertSql, String.valueOf(chunk.chunkId().value()), embeddingInput, metadataJson, vectorLiteral);
        }
        log.debug("vector upserted for chunkId={}, writeProfiles={}", chunk.chunkId().value(), profiles.size());
    }

    private List<WriteProfile> loadWriteProfiles() {
        String sql = """
                select profile_key, model_name, dimension, vector_type, storage_table
                  from indexing.embedding_profile
                 where write_enabled = true
                 order by profile_key
                """;
        return jdbcTemplate.query(sql, this::mapProfile);
    }

    private WriteProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new WriteProfile(
                rs.getString("profile_key"),
                rs.getString("model_name"),
                rs.getInt("dimension"),
                rs.getString("vector_type"),
                rs.getString("storage_table")
        );
    }

    private String safeTable(String storageTable) {
        if (storageTable == null || !QUALIFIED_IDENTIFIER.matcher(storageTable).matches()) {
            throw new IllegalArgumentException("invalid storage table identifier: " + storageTable);
        }
        return storageTable;
    }

    private String castType(String vectorType, int dimension) {
        String normalized = vectorType == null ? "" : vectorType.trim().toLowerCase();
        if (!"vector".equals(normalized) && !"halfvec".equals(normalized)) {
            throw new IllegalArgumentException("unsupported vector type: " + vectorType);
        }
        return normalized + "(" + dimension + ")";
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize vector metadata", ex);
        }
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
        if (targetDimension <= 0) {
            throw new IllegalArgumentException("invalid target dimension: " + targetDimension);
        }
        if (rawVector.length < targetDimension) {
            throw new IllegalStateException("embedding dimension too small, expected at least %d, actual=%d"
                    .formatted(targetDimension, rawVector.length));
        }
        if (rawVector.length == targetDimension) {
            return rawVector;
        }
        float[] projected = new float[targetDimension];
        System.arraycopy(rawVector, 0, projected, 0, targetDimension);
        return projected;
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    private record WriteProfile(
            String profileKey,
            String modelName,
            int dimension,
            String vectorType,
            String storageTable
    ) {
    }
}
