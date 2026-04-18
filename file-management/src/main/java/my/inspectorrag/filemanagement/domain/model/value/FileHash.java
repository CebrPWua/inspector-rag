package my.inspectorrag.filemanagement.domain.model.value;

public record FileHash(String value) {

    public FileHash {
        value = ValueObjectRules.requireHex64Lower(value, "fileHash");
    }

    public static FileHash of(String raw) {
        return new FileHash(raw);
    }
}
