package my.inspectorrag.embedding.domain.model.value;

public record TaskId(long value) {

    public TaskId {
        ValueObjectRules.requirePositive(value, "taskId");
    }

    public static TaskId of(long value) {
        return new TaskId(value);
    }
}
