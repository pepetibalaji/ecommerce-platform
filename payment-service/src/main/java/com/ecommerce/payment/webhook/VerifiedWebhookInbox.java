package com.ecommerce.payment.webhook;

import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifiedWebhookInbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    public record Receipt(UUID id,boolean duplicate) {}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Receipt accept(ProviderWebhookEvent event,String raw) {
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
            // Whitelisted provider envelope only; never persist raw request, card details or provider error text.
            String metadata=mapper.writeValueAsString(event.toBuilder().failureReason(null).build());
            UUID id=UUID.randomUUID();
            int inserted=jdbc.update("""
                    INSERT INTO payment_webhook_events(id,provider,provider_event_id,event_type,processing_status,payload_hash,received_at,verified_metadata)
                    VALUES(?,?,?,?,'RECEIVED',?,now(),?) ON CONFLICT(provider,provider_event_id) DO NOTHING
                    """,id,event.getProvider().name(),event.getProviderEventId(),event.getEventType(),hash,metadata);
            if(inserted==1) return new Receipt(id,false);
            return jdbc.queryForObject("SELECT id,payload_hash FROM payment_webhook_events WHERE provider=? AND provider_event_id=?",(rs,n)->{
                if(!hash.equals(rs.getString("payload_hash"))) {
                    metrics.counter("payment.webhook.duplicate.anomaly","provider",event.getProvider().name()).increment();
                    log.warn("payment_webhook_duplicate_payload_mismatch provider={} providerEventId={}",event.getProvider(),event.getProviderEventId());
                }
                return new Receipt(rs.getObject("id",UUID.class),true);
            },event.getProvider().name(),event.getProviderEventId());
        } catch(java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot retain verified webhook",ex);
        }
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(UUID id,RuntimeException ex) {
        jdbc.update("""
                UPDATE payment_webhook_events SET attempts=attempts+1,
                next_attempt_at=now()+(LEAST(3600,POWER(2,LEAST(attempts+1,11))) * interval '1 second'),
                last_error_code='WEBHOOK_TRANSITION_FAILED',processing_status='FAILED'
                WHERE id=? AND processing_status NOT IN ('PROCESSED','IGNORED')
                """,id);
        metrics.counter("payment.webhook.transition.failed").increment();
        log.error("payment_webhook_transition_failed eventId={} errorType={}",id,ex.getClass().getSimpleName());
    }
}
