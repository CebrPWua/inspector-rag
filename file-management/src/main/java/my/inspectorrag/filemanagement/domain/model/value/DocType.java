package my.inspectorrag.filemanagement.domain.model.value;

public record DocType(String value) {

    public DocType {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "docType", 64);
    }

    public static DocType of(String raw) {
        return new DocType(raw);
    }
}
