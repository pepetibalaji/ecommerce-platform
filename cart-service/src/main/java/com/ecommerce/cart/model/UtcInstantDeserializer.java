package com.ecommerce.cart.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Reads current ISO-8601 instants and legacy cart timestamps that were persisted without an offset.
 * Legacy values are interpreted as UTC because cart timestamps are service-generated instants.
 */
public final class UtcInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException exception) {
                return (Instant) context.handleWeirdStringValue(
                        Instant.class,
                        value,
                        "Expected an ISO-8601 instant or a legacy UTC local timestamp"
                );
            }
        }
    }
}
