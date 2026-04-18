package my.inspectorrag.filemanagement.domain.model.value;

import java.util.Objects;

public record LawCode(String value) {

    public LawCode {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "lawCode", 128);
    }

    public static LawCode of(String raw) {
        return new LawCode(raw);
    }

    public static LawCode ofOrFallback(String raw, FileHash fallbackFileHash) {
        Objects.requireNonNull(fallbackFileHash, "fallbackFileHash must not be null");
        String normalized = ValueObjectRules.normalizeOptional(raw, 128, "lawCode");
        if (normalized == null) {
            return new LawCode(fallbackFileHash.value());
        }
        return new LawCode(normalized);
    }
}
