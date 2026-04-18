package my.inspectorrag.filemanagement.domain.model.value;

public record SourceFileName(String value) {

    public SourceFileName {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "sourceFileName", 512);
    }

    public static SourceFileName of(String raw) {
        return new SourceFileName(raw);
    }
}
