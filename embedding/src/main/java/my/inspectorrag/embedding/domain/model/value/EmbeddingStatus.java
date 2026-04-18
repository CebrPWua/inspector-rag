package my.inspectorrag.embedding.domain.model.value;

import java.util.Arrays;

public enum EmbeddingStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    SUCCESS("success"),
    FAILED("failed"),
    SKIPPED("skipped");

    private final String dbValue;

    EmbeddingStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static EmbeddingStatus from(String raw) {
        String normalized = ValueObjectRules.requireTrimmedNonBlank(raw, "embeddingStatus", 32);
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid embeddingStatus: " + raw));
    }
}
