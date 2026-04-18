package my.inspectorrag.embedding.infrastructure.persistence.repository;

import my.inspectorrag.embedding.domain.model.EmbeddingProfile;
import my.inspectorrag.embedding.domain.model.value.ProfileKey;
import my.inspectorrag.embedding.domain.repository.EmbeddingProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ThreadLocalRandom;

@Repository
public class JdbcEmbeddingProfileRepository implements EmbeddingProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEmbeddingProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(EmbeddingProfile profile) {
        String sql = """
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
                sql,
                newId(),
                profile.profileKey().value(),
                profile.provider().value(),
                profile.modelName().value(),
                profile.dimension().value(),
                profile.vectorType().dbValue(),
                profile.distanceMetric().dbValue(),
                profile.storageTable().value(),
                profile.readEnabled(),
                profile.writeEnabled(),
                profile.defaultRead()
        );
    }

    @Override
    public void clearDefaultRead() {
        jdbcTemplate.update("update indexing.embedding_profile set is_default_read = false where is_default_read = true");
    }

    @Override
    public void activateDefaultRead(ProfileKey key) {
        int affected = jdbcTemplate.update(
                "update indexing.embedding_profile set is_default_read = true, read_enabled = true, updated_at = now() where profile_key = ?",
                key.value()
        );
        if (affected <= 0) {
            throw new IllegalArgumentException("embedding profile not found: " + key.value());
        }
    }

    @Override
    public void toggleWrite(ProfileKey key, boolean enabled) {
        int affected = jdbcTemplate.update(
                "update indexing.embedding_profile set write_enabled = ?, updated_at = now() where profile_key = ?",
                enabled,
                key.value()
        );
        if (affected <= 0) {
            throw new IllegalArgumentException("embedding profile not found: " + key.value());
        }
    }

    @Override
    public boolean existsByKey(ProfileKey key) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from indexing.embedding_profile where profile_key = ?",
                Integer.class,
                key.value()
        );
        return count != null && count > 0;
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
