package com.ecommerce.product.dto;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductContentValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll static void closeValidator() { FACTORY.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "INR", "EUR", "JPY"})
    void acceptsRealPriceCurrencies(String currency) {
        assertTrue(FACTORY.getValidator().validate(request(currency)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AAA", "usd", "US", "XXX", "XAU", " USD", "   "})
    void rejectsInventedMalformedAndNonPriceCurrencies(String currency) {
        assertTrue(FACTORY.getValidator().validate(request(currency)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("currency")));
        UpdateProductRequest update = UpdateProductRequest.builder().name("Phone")
                .price(BigDecimal.ONE).currency(currency).build();
        assertTrue(FACTORY.getValidator().validate(update).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("currency")));
    }

    @Test
    void rejectsNullBlankInsecureAndExcessiveImages() {
        for (List<String> images : List.of(Arrays.asList((String) null), List.of(""), List.of("http://cdn.example.com/x.jpg"),
                java.util.Collections.nCopies(11, "https://cdn.example.com/x.jpg"))) {
            CreateProductRequest request = request("USD");
            request.setImageUrls(images);
            assertFalse(FACTORY.getValidator().validate(request).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000000000000", "0.00001", "1E+6145", "12345678901234567890123456789012345"})
    void rejectsPricesBeyondTheDeclaredPrecisionWithoutRounding(String price) {
        CreateProductRequest create = request("USD");
        create.setPrice(new BigDecimal(price));
        UpdateProductRequest update = UpdateProductRequest.builder().name("Phone").currency("USD")
                .price(new BigDecimal(price)).build();
        assertTrue(FACTORY.getValidator().validate(create).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("price")));
        assertTrue(FACTORY.getValidator().validate(update).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("price")));
    }

    @Test
    void acceptsMaximumDeclaredPrecisionExactly() {
        CreateProductRequest create = request("INR");
        create.setPrice(new BigDecimal("999999999999999999.9999"));
        assertTrue(FACTORY.getValidator().validate(create).isEmpty());
        assertEquals(create.getPrice(), new org.bson.types.Decimal128(create.getPrice()).bigDecimalValue());
    }

    private CreateProductRequest request(String currency) {
        return CreateProductRequest.builder().name("Phone").price(BigDecimal.ONE).currency(currency).build();
    }
}
