package my.inspectorrag.filemanagement.domain.model.value;

public record StoragePath(String value) {

    public StoragePath {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "storagePath");
    }

    public static StoragePath of(String raw) {
        return new StoragePath(raw);
    }
}
