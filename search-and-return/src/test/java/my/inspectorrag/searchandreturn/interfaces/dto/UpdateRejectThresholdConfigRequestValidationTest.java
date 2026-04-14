package my.inspectorrag.searchandreturn.interfaces.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateRejectThresholdConfigRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRejectOutOfRangeAndInvalidEvidenceCount() {
        var request = new UpdateRejectThresholdConfigRequest(
                1.2,
                -0.1,
                0.08,
                0.7,
                0
        );

        var violations = validator.validate(request);
        assertEquals(3, violations.size());
    }

    @Test
    void shouldPassWhenAllFieldsAreInRange() {
        var request = new UpdateRejectThresholdConfigRequest(
                0.55,
                0.72,
                0.08,
                0.7,
                2
        );

        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
}
