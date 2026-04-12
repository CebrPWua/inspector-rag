package my.inspectorrag.searchandreturn.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderValidatorTest {

    @Test
    void validateShouldAllowMockAndSpringAi() {
        assertDoesNotThrow(() -> new AiProviderValidator("mock").validate());
        assertDoesNotThrow(() -> new AiProviderValidator("springai").validate());
    }

    @Test
    void validateShouldRejectUnknownProvider() {
        AiProviderValidator validator = new AiProviderValidator("oneapi");
        assertThrows(IllegalStateException.class, validator::validate);
    }
}
