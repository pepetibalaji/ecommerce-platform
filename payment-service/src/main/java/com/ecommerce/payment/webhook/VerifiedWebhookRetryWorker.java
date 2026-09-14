package com.ecommerce.payment.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VerifiedWebhookRetryWorker {
    private final JdbcTemplate jdbc;
    private final VerifiedWebhookProcessor processor;
    private final VerifiedWebhookInbox inbox;
    @Value("${payment.webhook.max-attempts:12}") private int maxAttempts;
    @Scheduled(fixedDelayString="${payment.webhook.retry-delay-ms:5000}")
    public void retry() {
        var ids=jdbc.query("""
                SELECT id FROM payment_webhook_events WHERE processing_status IN ('RECEIVED','FAILED')
                AND verified_metadata IS NOT NULL AND attempts<? AND next_attempt_at<=now()
                ORDER BY received_at LIMIT 50
                """,(rs,n)->rs.getObject(1,UUID.class),maxAttempts);
        for(UUID id:ids) try { processor.process(id); } catch(RuntimeException ex) { inbox.failed(id,ex); }
    }
}
