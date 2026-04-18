package my.inspectorrag.filemanagement.domain.model.value;

public record UploadBatchNo(String value) {

    public UploadBatchNo {
        value = ValueObjectRules.requireTrimmedNonBlank(value, "uploadBatchNo", 64);
    }

    public static UploadBatchNo of(String raw) {
        return new UploadBatchNo(raw);
    }

    public static UploadBatchNo ofNullable(String raw) {
        String normalized = ValueObjectRules.normalizeOptional(raw, 64, "uploadBatchNo");
        if (normalized == null) {
            return null;
        }
        return new UploadBatchNo(normalized);
    }
}
