package com.ecommerce.payment.config;

import com.ecommerce.common.events.payment.PaymentExpiredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventSerializationTest {
    @Test void kafkaOutcomeUsesUtcStringAndCompleteStableEnvelope() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        var factory=new DefaultKafkaProducerFactory<Object,Object>(Map.of());
        new PaymentKafkaReliabilityConfiguration().paymentEventJsonSerializer(mapper).customize(factory);
        var event=new PaymentExpiredEvent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("10.00"),
                "INR","STRIPE","correlation","trace");
        event.setOccurredAt(Instant.parse("2026-09-14T12:30:00Z"));
        var wire=mapper.readTree(factory.getValueSerializer().serialize("payment-expired",event));
        assertThat(wire.get("occurredAt").asText()).isEqualTo("2026-09-14T12:30:00Z");
        assertThat(wire.get("occurredAt").isTextual()).isTrue();
        assertThat(wire.get("eventId").asText()).isEqualTo(event.getEventId().toString());
        for(String field:new String[]{"paymentId","orderId","userId","amount","currency","provider","correlationId","traceId","schemaVersion"})
            assertThat(wire.hasNonNull(field)).as(field).isTrue();
    }
}
