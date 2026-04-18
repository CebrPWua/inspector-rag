package my.inspectorrag.filemanagement.domain.model.value;

public record DocumentId(long value) {

    public DocumentId {
        ValueObjectRules.requirePositive(value, "docId");
    }

    public static DocumentId of(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("docId must not be null");
        }
        return new DocumentId(value);
    }
}
