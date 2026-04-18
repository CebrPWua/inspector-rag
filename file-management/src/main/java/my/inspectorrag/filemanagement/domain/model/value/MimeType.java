package my.inspectorrag.filemanagement.domain.model.value;

public record MimeType(String value) {

    public MimeType {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "mimeType", 127);
    }

    public static MimeType of(String raw) {
        return new MimeType(raw);
    }
}
