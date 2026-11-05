package one.xis.seabattle.webapp.validation;

import one.xis.validation.ValidatorException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangeValidatorTest {
    @Range(min = 0, max = 3) Number bounded;
    @Range(min = 1) Number lowerBound;
    @Range(max = 4) Number upperBound;
    @Range(min = 0, minInclusive = false) Number positive;

    @Test void limitsAreInclusiveAndApplyToBothIntegralAndDecimalValues() throws Exception {
        var validator = new RangeValidator();
        var field = getClass().getDeclaredField("bounded");
        for (Number value : new Number[] {0, 3, .25, 2L})
            assertDoesNotThrow(() -> validator.validate(value, field, null));
        for (Number value : new Number[] {-1, 4, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(ValidatorException.class, () -> validator.validate(value, field, null));
        assertDoesNotThrow(() -> validator.validate(null, field, null));
    }

    @Test void eitherLimitCanBeUsedAlone() throws Exception {
        var validator = new RangeValidator();
        assertThrows(ValidatorException.class, () -> validator.validate(0, getClass().getDeclaredField("lowerBound"), null));
        assertDoesNotThrow(() -> validator.validate(999, getClass().getDeclaredField("lowerBound"), null));
        assertThrows(ValidatorException.class, () -> validator.validate(5, getClass().getDeclaredField("upperBound"), null));
        assertDoesNotThrow(() -> validator.validate(-999, getClass().getDeclaredField("upperBound"), null));
    }
    @Test void anExclusiveMinimumRejectsTheBoundary() throws Exception {
        var validator = new RangeValidator();
        var field = getClass().getDeclaredField("positive");
        assertThrows(ValidatorException.class, () -> validator.validate(0, field, null));
        assertDoesNotThrow(() -> validator.validate(.01, field, null));
    }

}
