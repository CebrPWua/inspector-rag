package my.inspectorrag.embedding.domain.model.value;

import java.util.Arrays;

public enum TaskStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    SUCCESS("success"),
    FAILED("failed");

    private final String dbValue;

    TaskStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static TaskStatus from(String raw) {
        String normalized = ValueObjectRules.requireTrimmedNonBlank(raw, "taskStatus", 32);
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid taskStatus: " + raw));
    }
}
