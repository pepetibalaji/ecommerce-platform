package com.ecommerce.product.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

public class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {
    private static final Set<String> CODES = Currency.getAvailableCurrencies().stream()
            .filter(currency -> currency.getDefaultFractionDigits() >= 0)
            .map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required/null validation belongs to @NotBlank.
        return value == null || CODES.contains(value);
    }
}
