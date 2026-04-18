package my.inspectorrag.embedding.domain.model.value;

public record ChunkId(long value) {

    public ChunkId {
        ValueObjectRules.requirePositive(value, "chunkId");
    }

    public static ChunkId of(long value) {
        return new ChunkId(value);
    }
}
