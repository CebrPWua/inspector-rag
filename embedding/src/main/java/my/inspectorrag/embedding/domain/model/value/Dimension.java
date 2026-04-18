package my.inspectorrag.embedding.domain.model.value;

public record Dimension(int value) {

    public static final int MAX = 16384;

    public Dimension {
        if (value <= 0 || value > MAX) {
            throw new IllegalArgumentException("dimension must be in (0, " + MAX + "], got: " + value);
        }
    }

    public static Dimension of(int value) {
        return new Dimension(value);
    }
}
