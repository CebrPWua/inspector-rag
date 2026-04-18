package my.inspectorrag.filemanagement.domain.model.value;

import java.util.Arrays;

public enum DocumentStatus {
    ACTIVE("active"),
    INACTIVE("inactive"),
    PENDING_CONFIRM("pending_confirm");

    private final String dbValue;

    DocumentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static DocumentStatus from(String raw) {
        String normalized = ValueObjectRules.requireTrimmedNonBlank(raw, "status", 32);
        return Arrays.stream(values())
                .filter(value -> value.dbValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid status: " + raw));
    }
}
