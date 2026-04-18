package my.inspectorrag.filemanagement.domain.model.value;

import java.util.regex.Pattern;

final class ValueObjectRules {

    private static final Pattern HEX64_LOWER = Pattern.compile("^[0-9a-f]{64}$");

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

    static String normalizeOptional(String raw, int maxLength, String fieldName) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
        return normalized;
    }

    static long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static String requireHex64Lower(String raw, String fieldName) {
        String normalized = requireTrimmedNonBlank(raw, fieldName);
        if (!HEX64_LOWER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a 64-char lowercase hex string");
        }
        return normalized;
    }
}
