package my.inspectorrag.filemanagement.domain.model.value;

public record VersionNo(String value) {

    private static final String DEFAULT_VERSION = "v1";

    public VersionNo {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "versionNo", 64);
    }

    public static VersionNo of(String raw) {
        return new VersionNo(raw);
    }

    public static VersionNo ofOrDefault(String raw) {
        String normalized = ValueObjectRules.normalizeOptional(raw, 64, "versionNo");
        if (normalized == null) {
            return new VersionNo(DEFAULT_VERSION);
        }
        return new VersionNo(normalized);
    }
}
