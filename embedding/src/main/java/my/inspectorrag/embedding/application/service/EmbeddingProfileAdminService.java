package my.inspectorrag.embedding.application.service;

import my.inspectorrag.embedding.domain.model.EmbeddingProfile;
import my.inspectorrag.embedding.domain.model.value.ProfileKey;
import my.inspectorrag.embedding.domain.repository.EmbeddingProfileRepository;
import my.inspectorrag.embedding.domain.service.ProfileStorageInitializer;
import my.inspectorrag.embedding.interfaces.dto.CreateEmbeddingProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingProfileAdminService {

    private final EmbeddingProfileRepository profileRepository;
    private final ProfileStorageInitializer profileStorageInitializer;

    public EmbeddingProfileAdminService(
            EmbeddingProfileRepository profileRepository,
            ProfileStorageInitializer profileStorageInitializer
    ) {
        this.profileRepository = profileRepository;
        this.profileStorageInitializer = profileStorageInitializer;
    }

    @Transactional
    public void createProfile(CreateEmbeddingProfileRequest request) {
        var profile = EmbeddingProfile.create(
                request.profileKey(),
                request.provider(),
                request.modelName(),
                request.dimension(),
                request.vectorType(),
                request.distanceMetric(),
                request.storageTable(),
                request.readEnabled(),
                request.writeEnabled(),
                request.defaultRead()
        );

        profileStorageInitializer.initStorage(profile);

        if (profile.defaultRead()) {
            profileRepository.clearDefaultRead();
        }
        profileRepository.save(profile);
    }

    @Transactional
    public void activateReadProfile(String profileKey) {
        ProfileKey key = ProfileKey.of(profileKey);
        if (!profileRepository.existsByKey(key)) {
            throw new IllegalArgumentException("embedding profile not found: " + profileKey);
        }
        profileRepository.clearDefaultRead();
        profileRepository.activateDefaultRead(key);
    }

    @Transactional
    public void toggleWriteProfile(String profileKey, boolean enabled) {
        profileRepository.toggleWrite(ProfileKey.of(profileKey), enabled);
    }
}
