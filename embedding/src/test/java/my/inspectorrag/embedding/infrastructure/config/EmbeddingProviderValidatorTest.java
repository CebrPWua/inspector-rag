package my.inspectorrag.embedding.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingProviderValidatorTest {

    @Test
    void validateShouldAllowMockAndSpringAi() {
        assertDoesNotThrow(() -> new EmbeddingProviderValidator("mock").validate());
        assertDoesNotThrow(() -> new EmbeddingProviderValidator("springai").validate());
    }

    @Test
    void validateShouldRejectUnknownProvider() {
        EmbeddingProviderValidator validator = new EmbeddingProviderValidator("oneapi");
        assertThrows(IllegalStateException.class, validator::validate);
    }
}
