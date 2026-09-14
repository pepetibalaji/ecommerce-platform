package com.ecommerce.payment.outbox;

import com.ecommerce.common.events.payment.*;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    @Value("${payment.outbox.max-attempts:12}") private int maxAttempts;
    @Value("${payment.outbox.batch-size:25}") private int batchSize;
    @Value("${payment.outbox.send-timeout-seconds:10}") private int sendTimeoutSeconds;
    record Delivery(UUID id, UUID orderId, String topic, String payload, int attempts, UUID token) {}

    @Scheduled(fixedDelayString = "${payment.outbox.poll-delay-ms:1000}")
    public void deliver() {
        // Claim one at a time: a batch waiting behind a slow send must not outlive its leases.
        for (int i = 0; i < batchSize; i++) {
            Delivery delivery = claim();
            if (delivery == null) return;
            try {
                kafka.send(delivery.topic(), delivery.orderId().toString(), decode(delivery))
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                int updated = jdbc.update("""
                        UPDATE payment_event_outbox SET status='DELIVERED',delivered_at=now(),
                        lease_token=NULL,lease_until=NULL,last_error_code=NULL WHERE id=? AND lease_token=?
                        """, delivery.id(), delivery.token());
                if (updated == 1) metrics.counter("payment.outbox.delivered", "topic", delivery.topic()).increment();
                log.info("payment_outbox_delivered eventId={} orderId={} topic={} attempt={}",
                        delivery.id(), delivery.orderId(), delivery.topic(), delivery.attempts());
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                boolean dead = delivery.attempts() >= maxAttempts;
                jdbc.update("""
                        UPDATE payment_event_outbox SET status=?, next_attempt_at=now()+(? * interval '1 second'),
                        lease_token=NULL,lease_until=NULL,last_error_code='KAFKA_DELIVERY_FAILED'
                        WHERE id=? AND lease_token=?
                        """, dead ? "DEAD" : "PENDING", backoffSeconds(delivery.attempts()), delivery.id(), delivery.token());
                metrics.counter(dead ? "payment.outbox.terminal" : "payment.outbox.retry", "topic", delivery.topic()).increment();
                log.error("payment_outbox_delivery_failed eventId={} orderId={} topic={} attempt={} terminal={} errorType={}",
                        delivery.id(), delivery.orderId(), delivery.topic(), delivery.attempts(), dead, ex.getClass().getSimpleName());
                if (Thread.currentThread().isInterrupted()) return;
            }
        }
    }

    Delivery claim() {
        return transactions.execute(tx -> {
            UUID token = UUID.randomUUID();
            List<Delivery> rows = jdbc.query("""
                    WITH candidate AS (
                      SELECT o.id FROM payment_event_outbox o
                      WHERE ((o.status='PENDING' AND o.next_attempt_at<=now()) OR
                             (o.status='LEASED' AND o.lease_until<now()))
                        AND NOT EXISTS (SELECT 1 FROM payment_event_outbox older
                            WHERE older.order_id=o.order_id AND older.sequence<o.sequence AND older.status<>'DELIVERED')
                      ORDER BY o.sequence LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                    )
                    UPDATE payment_event_outbox o SET status='LEASED', attempts=attempts+1,
                        lease_token=?,lease_until=now()+(? * interval '1 second')
                    FROM candidate c WHERE o.id=c.id RETURNING o.*
                    """, (rs, n) -> new Delivery(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                    rs.getString("topic"), rs.getString("payload"), rs.getInt("attempts"), token),
                    token, Math.max(30, sendTimeoutSeconds * 2));
            return rows.isEmpty() ? null : rows.getFirst();
        });
    }

    static long backoffSeconds(int attempt) { return Math.min(3600, 2L << Math.min(11, Math.max(0, attempt - 1))); }

    private Object decode(Delivery row) throws Exception {
        Class<?> type = switch (row.topic()) {
            case KafkaTopics.PAYMENT_SUCCESS -> PaymentSuccessEvent.class;
            case KafkaTopics.PAYMENT_FAILED -> PaymentFailedEvent.class;
            case KafkaTopics.PAYMENT_EXPIRED -> PaymentExpiredEvent.class;
            case KafkaTopics.PAYMENT_REFUND_COMPLETED -> PaymentRefundCompletedEvent.class;
            case KafkaTopics.PAYMENT_REFUND_FAILED -> PaymentRefundFailedEvent.class;
            case KafkaTopics.PAYMENT_REFUND_REQUEST_REJECTED -> PaymentRefundRequestRejectedEvent.class;
            default -> throw new IllegalStateException("Unsupported payment outbox topic");
        };
        return mapper.readValue(row.payload(), type);
    }
}
