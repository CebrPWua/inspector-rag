package my.inspectorrag.embedding.domain.model.value;

public record DocumentId(long value) {

    public DocumentId {
        ValueObjectRules.requirePositive(value, "docId");
    }

    public static DocumentId of(long value) {
        return new DocumentId(value);
    }
}
