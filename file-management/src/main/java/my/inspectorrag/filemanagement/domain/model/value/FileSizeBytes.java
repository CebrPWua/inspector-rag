package my.inspectorrag.filemanagement.domain.model.value;

public record FileSizeBytes(long value) {

    public FileSizeBytes {
        ValueObjectRules.requirePositive(value, "fileSizeBytes");
    }

    public static FileSizeBytes of(long raw) {
        return new FileSizeBytes(raw);
    }
}
