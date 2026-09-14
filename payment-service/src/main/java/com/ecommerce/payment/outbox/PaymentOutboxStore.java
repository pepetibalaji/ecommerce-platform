package com.ecommerce.payment.outbox;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Joins the state transition transaction. A send is never performed on this path. */
@Service
@RequiredArgsConstructor
public class PaymentOutboxStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String outcomeKey, UUID paymentId, UUID orderId, String topic, AbstractDomainEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO payment_event_outbox(id,outcome_key,payment_id,order_id,topic,payload)
                    VALUES (?,?,?,?,?,?) ON CONFLICT(outcome_key) DO NOTHING
                    """, event.getEventId(), outcomeKey, paymentId, orderId, topic,
                    mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payment event serialization failed", ex);
        }
    }
}
