package my.inspectorrag.filemanagement.domain.model.value;

import java.util.Arrays;

public enum ParseStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    SUCCESS("success"),
    FAILED("failed");

    private final String dbValue;

    ParseStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public boolean canDelete() {
        return this == SUCCESS || this == FAILED;
    }

    public static ParseStatus from(String raw) {
        String normalized = ValueObjectRules.requireTrimmedNonBlank(raw, "parseStatus", 32);
        return Arrays.stream(values())
                .filter(value -> value.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid parseStatus: " + raw));
    }
}
