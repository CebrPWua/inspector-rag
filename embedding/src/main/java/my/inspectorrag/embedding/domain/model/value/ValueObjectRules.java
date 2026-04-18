package my.inspectorrag.embedding.domain.model.value;

import java.util.regex.Pattern;

final class ValueObjectRules {

    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private ValueObjectRules() {
    }

    static String requireTrimmedNonBlank(String raw, String fieldName, int maxLength) {
        if (raw == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
        return normalized;
    }

    static String requireTrimmedNonBlank(String raw, String fieldName) {
        if (raw == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    static long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static int requirePositiveInt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static String requireQualifiedIdentifier(String raw, String fieldName) {
        String normalized = requireTrimmedNonBlank(raw, fieldName, 128);
        if (!QUALIFIED_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a qualified SQL identifier: " + raw);
        }
        return normalized;
    }
}
