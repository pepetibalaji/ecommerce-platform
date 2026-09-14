package com.ecommerce.payment.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payments/operations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PaymentOperationsController {
    private final JdbcTemplate jdbc;

    @GetMapping("/outbox")
    public List<Map<String,Object>> status() {
        return jdbc.queryForList("SELECT status,count(*) AS count,min(created_at) AS oldest_created_at FROM payment_event_outbox GROUP BY status ORDER BY status");
    }

    @PostMapping("/outbox/{eventId}/retry")
    public Map<String,Object> retry(@PathVariable UUID eventId, Authentication actor) {
        int updated=jdbc.update("UPDATE payment_event_outbox SET status='PENDING',attempts=0,next_attempt_at=now(),last_error_code=NULL WHERE id=? AND status='DEAD'",eventId);
        log.warn("payment_outbox_manual_retry eventId={} actorId={} changed={}",eventId,actor.getName(),updated);
        return Map.of("eventId",eventId,"requeued",updated==1);
    }

    @GetMapping("/webhooks")
    public List<Map<String,Object>> webhooks() {
        return jdbc.queryForList("SELECT processing_status,count(*) AS count,min(received_at) AS oldest_received_at FROM payment_webhook_events GROUP BY processing_status ORDER BY processing_status");
    }

    @PostMapping("/webhooks/{eventId}/retry")
    public Map<String,Object> retryWebhook(@PathVariable UUID eventId,Authentication actor) {
        int updated=jdbc.update("UPDATE payment_webhook_events SET processing_status='RECEIVED',attempts=0,next_attempt_at=now(),last_error_code=NULL WHERE id=? AND processing_status='FAILED' AND verified_metadata IS NOT NULL",eventId);
        log.warn("payment_webhook_manual_retry eventId={} actorId={} changed={}",eventId,actor.getName(),updated);
        return Map.of("eventId",eventId,"requeued",updated==1);
    }
}
