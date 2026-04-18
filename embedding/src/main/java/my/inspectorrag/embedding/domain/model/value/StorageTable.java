package my.inspectorrag.embedding.domain.model.value;

public record StorageTable(String value) {

    public StorageTable {
        value = ValueObjectRules.requireQualifiedIdentifier(value, "storageTable");
    }

    public static StorageTable of(String value) {
        return new StorageTable(value);
    }

    public String indexPrefix() {
        return value.replace('.', '_');
    }
}
