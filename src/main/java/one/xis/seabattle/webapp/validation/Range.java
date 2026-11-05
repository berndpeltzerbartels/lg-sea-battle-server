package one.xis.seabattle.webapp.validation;

import one.xis.validation.Validate;
import java.lang.annotation.*;

/** Numeric limits; optional values use @Mandatory separately. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Validate(validatorClass = RangeValidator.class, messageKey = "validation.range", globalMessageKey = "validation.range")
public @interface Range {
    double min() default -Double.MAX_VALUE;
    boolean minInclusive() default true;
    double max() default Double.MAX_VALUE;
}
