package my.inspectorrag.embedding.domain.model.value;

import java.util.Arrays;
import java.util.Locale;

public enum VectorType {
    VECTOR("vector", 2000),
    HALFVEC("halfvec", 4000);

    private final String dbValue;
    private final int annLimit;

    VectorType(String dbValue, int annLimit) {
        this.dbValue = dbValue;
        this.annLimit = annLimit;
    }

    public String dbValue() {
        return dbValue;
    }

    public int annLimit() {
        return annLimit;
    }

    public static VectorType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("vectorType must not be blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported vectorType: " + raw));
    }
}
