package com.ecommerce.product.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = IsoCurrencyValidator.class)
public @interface IsoCurrency {
    String message() default "Currency must be a supported uppercase ISO 4217 code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
