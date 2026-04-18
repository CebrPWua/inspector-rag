package my.inspectorrag.embedding.domain.model.value;

public record Provider(String value) {

    public Provider {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "provider");
    }

    public static Provider of(String value) {
        return new Provider(value);
    }
}
