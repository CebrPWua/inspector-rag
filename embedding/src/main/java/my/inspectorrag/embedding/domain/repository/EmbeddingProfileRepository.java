package my.inspectorrag.embedding.domain.repository;

import my.inspectorrag.embedding.domain.model.value.ProfileKey;
import my.inspectorrag.embedding.domain.model.EmbeddingProfile;

public interface EmbeddingProfileRepository {

    void save(EmbeddingProfile profile);

    void clearDefaultRead();

    void activateDefaultRead(ProfileKey key);

    void toggleWrite(ProfileKey key, boolean enabled);

    boolean existsByKey(ProfileKey key);
}
