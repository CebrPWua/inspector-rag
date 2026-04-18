package my.inspectorrag.embedding.domain.service;

import my.inspectorrag.embedding.domain.model.EmbeddingProfile;

public interface ProfileStorageInitializer {

    void initStorage(EmbeddingProfile profile);
}
