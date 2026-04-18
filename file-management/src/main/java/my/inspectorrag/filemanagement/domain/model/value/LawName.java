package my.inspectorrag.filemanagement.domain.model.value;

public record LawName(String value) {

    public LawName {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "lawName", 512);
    }

    public static LawName of(String raw) {
        return new LawName(raw);
    }
}
