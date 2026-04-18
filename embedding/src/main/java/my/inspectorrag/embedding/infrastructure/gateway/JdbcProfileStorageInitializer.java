package my.inspectorrag.embedding.infrastructure.gateway;

import my.inspectorrag.embedding.domain.model.EmbeddingProfile;
import my.inspectorrag.embedding.domain.service.ProfileStorageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcProfileStorageInitializer implements ProfileStorageInitializer {

    private static final Logger log = LoggerFactory.getLogger(JdbcProfileStorageInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileStorageInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initStorage(EmbeddingProfile profile) {
        createTable(profile);
        createAnnIndex(profile);
        createMetadataIndex(profile);
    }

    private void createTable(EmbeddingProfile profile) {
        String sql = """
                create table if not exists %s (
                    id text primary key,
                    content text,
                    metadata jsonb,
                    embedding %s not null,
                    created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now()
                )
                """.formatted(profile.storageTable().value(), profile.castType());
        jdbcTemplate.execute(sql);
    }

    private void createAnnIndex(EmbeddingProfile profile) {
        if (profile.isAnnIndexSupported()) {
            String sql = """
                    create index if not exists %s
                        on %s using hnsw (embedding %s_%s_ops)
                    """.formatted(
                    profile.hnswIndexName(),
                    profile.storageTable().value(),
                    profile.vectorType().dbValue(),
                    profile.distanceMetric().dbValue()
            );
            jdbcTemplate.execute(sql);
        } else if (profile.needsQuantizedIndex()) {
            String sql = """
                    create index if not exists %s
                        on %s using hnsw ((binary_quantize(embedding)::bit(%d)) bit_hamming_ops)
                    """.formatted(
                    profile.quantizedIndexName(),
                    profile.storageTable().value(),
                    profile.dimension().value()
            );
            jdbcTemplate.execute(sql);
        } else {
            log.warn("skip ANN index creation for table={} because vector_type={} dimension={} exceeds pgvector ANN limit",
                    profile.storageTable().value(), profile.vectorType().dbValue(), profile.dimension().value());
        }
    }

    private void createMetadataIndex(EmbeddingProfile profile) {
        String sql = """
                create index if not exists %s
                    on %s using gin (metadata)
                """.formatted(profile.metadataIndexName(), profile.storageTable().value());
        jdbcTemplate.execute(sql);
    }
}
