package my.inspectorrag.embedding.domain.model.value;

public record ModelName(String value) {

    public ModelName {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "modelName", 128);
    }

    public static ModelName of(String value) {
        return new ModelName(value);
    }
}
