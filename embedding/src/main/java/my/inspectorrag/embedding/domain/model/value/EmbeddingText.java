package my.inspectorrag.embedding.domain.model.value;

public record EmbeddingText(String value) {

    public EmbeddingText {
        ValueObjectRules.requireTrimmedNonBlank(value, "embeddingText");
    }
}
