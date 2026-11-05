package one.xis.seabattle.webapp.validation;

import one.xis.UserContext;
import one.xis.context.Component;
import one.xis.validation.Validator;
import one.xis.validation.ValidatorException;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

@Component
public class RangeValidator implements Validator<Number> {
    @Override
    public void validate(Number value, AnnotatedElement element, UserContext user) throws ValidatorException {
        if (value == null) return;
        Range range = element.getAnnotation(Range.class);
        if (!Double.isFinite(range.min()) || !Double.isFinite(range.max()) || range.min() > range.max()) throw new IllegalStateException("Ungültige Range-Annotation.");
        double number = value.doubleValue();
        String reason = !Double.isFinite(number) ? "muss eine endliche Zahl sein"
                : number < range.min() || !range.minInclusive() && number == range.min()
                ? "muss " + (range.minInclusive() ? "mindestens " : "größer als ") + limit(range.min()) + " sein"
                : number > range.max() ? "darf höchstens " + limit(range.max()) + " betragen" : null;
        if (reason != null) throw new ValidatorException(Map.of("reason", reason));
    }

    private String limit(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
