package my.inspectorrag.embedding.domain.model;

import my.inspectorrag.embedding.domain.model.value.Dimension;
import my.inspectorrag.embedding.domain.model.value.DistanceMetric;
import my.inspectorrag.embedding.domain.model.value.ModelName;
import my.inspectorrag.embedding.domain.model.value.ProfileKey;
import my.inspectorrag.embedding.domain.model.value.Provider;
import my.inspectorrag.embedding.domain.model.value.StorageTable;
import my.inspectorrag.embedding.domain.model.value.VectorType;

import java.util.Objects;

public record EmbeddingProfile(
        ProfileKey profileKey,
        Provider provider,
        ModelName modelName,
        Dimension dimension,
        VectorType vectorType,
        DistanceMetric distanceMetric,
        StorageTable storageTable,
        boolean readEnabled,
        boolean writeEnabled,
        boolean defaultRead
) {

    public EmbeddingProfile {
        Objects.requireNonNull(profileKey, "profileKey must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(vectorType, "vectorType must not be null");
        Objects.requireNonNull(distanceMetric, "distanceMetric must not be null");
        Objects.requireNonNull(storageTable, "storageTable must not be null");
    }

    public static EmbeddingProfile create(
            String profileKey,
            String provider,
            String modelName,
            int dimension,
            String vectorType,
            String distanceMetric,
            String storageTable,
            boolean readEnabled,
            boolean writeEnabled,
            boolean defaultRead
    ) {
        return new EmbeddingProfile(
                ProfileKey.of(profileKey),
                Provider.of(provider),
                ModelName.of(modelName),
                Dimension.of(dimension),
                VectorType.from(vectorType),
                DistanceMetric.from(distanceMetric),
                StorageTable.of(storageTable),
                readEnabled,
                writeEnabled,
                defaultRead
        );
    }

    public boolean isAnnIndexSupported() {
        return dimension.value() <= vectorType.annLimit();
    }

    public boolean needsQuantizedIndex() {
        return vectorType == VectorType.HALFVEC
                && dimension.value() > vectorType.annLimit()
                && distanceMetric == DistanceMetric.COSINE;
    }

    public String hnswIndexName() {
        return storageTable.indexPrefix() + "_hnsw_" + distanceMetric.dbValue();
    }

    public String quantizedIndexName() {
        return storageTable.indexPrefix() + "_hnsw_bq";
    }

    public String metadataIndexName() {
        return storageTable.indexPrefix() + "_metadata_gin";
    }

    public String castType() {
        return vectorType.dbValue() + "(" + dimension.value() + ")";
    }
}
