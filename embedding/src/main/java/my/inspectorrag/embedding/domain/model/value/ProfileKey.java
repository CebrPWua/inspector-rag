package my.inspectorrag.embedding.domain.model.value;

public record ProfileKey(String value) {

    public ProfileKey {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "profileKey", 64);
    }

    public static ProfileKey of(String value) {
        return new ProfileKey(value);
    }
}
