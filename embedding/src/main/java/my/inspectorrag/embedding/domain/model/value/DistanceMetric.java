package my.inspectorrag.embedding.domain.model.value;

import java.util.Arrays;
import java.util.Locale;

public enum DistanceMetric {
    COSINE("cosine"),
    L2("l2"),
    IP("ip");

    private final String dbValue;

    DistanceMetric(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static DistanceMetric from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("distanceMetric must not be blank");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported distanceMetric: " + raw));
    }
}
