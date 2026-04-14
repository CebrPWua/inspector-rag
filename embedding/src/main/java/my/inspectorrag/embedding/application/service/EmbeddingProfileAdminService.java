package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.interfaces.dto.CreateEmbeddingProfileRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class EmbeddingProfileAdminService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProfileAdminService.class);
    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingProfileAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void createProfile(CreateEmbeddingProfileRequest request) {
        String table = safeTable(request.storageTable());
        String vectorType = normalizedVectorType(request.vectorType());
        String distanceMetric = normalizedDistanceMetric(request.distanceMetric());

        String createTable = """
                create table if not exists %s (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding %s(%d) not null,
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """.formatted(table, vectorType, request.dimension());
        jdbcTemplate.execute(createTable);

        if (isAnnIndexSupported(vectorType, request.dimension())) {
            String indexName = table.replace('.', '_') + "_hnsw_" + distanceMetric;
            String createIndex = """
                    create index if not exists %s
                        on %s using hnsw (embedding %s_%s_ops)
                    """.formatted(indexName, table, vectorType, distanceMetric);
            jdbcTemplate.execute(createIndex);
        } else if ("halfvec".equals(vectorType) && request.dimension() > 4000 && "cosine".equals(distanceMetric)) {
            String quantizedIndexName = table.replace('.', '_') + "_hnsw_bq";
            String quantizedIndexSql = """
                    create index if not exists %s
                        on %s using hnsw ((binary_quantize(embedding)::bit(%d)) bit_hamming_ops)
                    """.formatted(quantizedIndexName, table, request.dimension());
            jdbcTemplate.execute(quantizedIndexSql);
        } else {
            log.warn("skip ANN index creation for table={} because vector_type={} dimension={} exceeds pgvector ANN limit",
                    table, vectorType, request.dimension());
        }

        String metadataIndexName = table.replace('.', '_') + "_metadata_gin";
        String createMetadataIndex = """
                create index if not exists %s
                    on %s using gin (metadata)
                """.formatted(metadataIndexName, table);
        jdbcTemplate.execute(createMetadataIndex);

        if (request.defaultRead()) {
            jdbcTemplate.update("update indexing.embedding_profile set is_default_read = false where is_default_read = true");
        }

        String insertSql = """
                insert into indexing.embedding_profile
                (id, profile_key, provider, model_name, dimension, vector_type, distance_metric, storage_table,
                 read_enabled, write_enabled, is_default_read, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (profile_key) do update
                set provider = excluded.provider,
                    model_name = excluded.model_name,
                    dimension = excluded.dimension,
                    vector_type = excluded.vector_type,
                    distance_metric = excluded.distance_metric,
                    storage_table = excluded.storage_table,
                    read_enabled = excluded.read_enabled,
                    write_enabled = excluded.write_enabled,
                    is_default_read = excluded.is_default_read,
                    updated_at = now()
                """;
        jdbcTemplate.update(
                insertSql,
                newId(),
                request.profileKey(),
                request.provider(),
                request.modelName(),
                request.dimension(),
                vectorType,
                distanceMetric,
                table,
                request.readEnabled(),
                request.writeEnabled(),
                request.defaultRead()
        );
    }

    @Transactional
    public void activateReadProfile(String profileKey) {
        int exists = jdbcTemplate.queryForObject(
                "select count(1) from indexing.embedding_profile where profile_key = ?",
                Integer.class,
                profileKey
        );
        if (exists <= 0) {
            throw new IllegalArgumentException("embedding profile not found: " + profileKey);
        }
        jdbcTemplate.update("update indexing.embedding_profile set is_default_read = false where is_default_read = true");
        jdbcTemplate.update(
                "update indexing.embedding_profile set is_default_read = true, read_enabled = true, updated_at = now() where profile_key = ?",
                profileKey
        );
    }

    @Transactional
    public void toggleWriteProfile(String profileKey, boolean enabled) {
        int affected = jdbcTemplate.update(
                "update indexing.embedding_profile set write_enabled = ?, updated_at = now() where profile_key = ?",
                enabled,
                profileKey
        );
        if (affected <= 0) {
            throw new IllegalArgumentException("embedding profile not found: " + profileKey);
        }
    }

    private String safeTable(String table) {
        if (table == null || !QUALIFIED_IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("invalid storage table identifier: " + table);
        }
        return table;
    }

    private String normalizedVectorType(String vectorType) {
        String normalized = vectorType == null ? "" : vectorType.trim().toLowerCase(Locale.ROOT);
        if (!"vector".equals(normalized) && !"halfvec".equals(normalized)) {
            throw new IllegalArgumentException("unsupported vector type: " + vectorType);
        }
        return normalized;
    }

    private String normalizedDistanceMetric(String distanceMetric) {
        String normalized = distanceMetric == null ? "" : distanceMetric.trim().toLowerCase(Locale.ROOT);
        if (!"cosine".equals(normalized) && !"l2".equals(normalized) && !"ip".equals(normalized)) {
            throw new IllegalArgumentException("unsupported distance metric: " + distanceMetric);
        }
        return normalized;
    }

    private boolean isAnnIndexSupported(String vectorType, int dimension) {
        if ("halfvec".equals(vectorType)) {
            return dimension <= 4000;
        }
        if ("vector".equals(vectorType)) {
            return dimension <= 2000;
        }
        return false;
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
