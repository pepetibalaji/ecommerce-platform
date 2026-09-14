package com.ecommerce.payment.webhook;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifiedWebhookProcessor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentRefundRepository refunds;
    private final PaymentEventPublisher publisher;
    private final EntityManager entities;
    private final MeterRegistry metrics;
    @Value("${payment.webhook.max-attempts:12}") private int maxAttempts;
    private static final Set<PaymentStatus> OPEN=Set.of(PaymentStatus.PENDING,PaymentStatus.REQUIRES_CUSTOMER_ACTION,PaymentStatus.PROCESSING);

    @Transactional
    public WebhookProcessingStatus process(UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM payment_webhook_events WHERE id=? FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty()) return WebhookProcessingStatus.RECEIVED;
        var row=rows.getFirst();
        String prior=(String)row.get("processing_status");
        if(prior.equals("PROCESSED") || prior.equals("IGNORED")) return WebhookProcessingStatus.valueOf(prior);
        int count=((Number)row.get("attempts")).intValue();
        if(count>=maxAttempts || row.get("verified_metadata")==null) return WebhookProcessingStatus.FAILED;
        try {
            JsonNode e=mapper.readTree((String)row.get("verified_metadata"));
            PaymentProvider provider=PaymentProvider.valueOf((String)row.get("provider"));
            if(text(e,"providerRefundId")!=null || text(e,"refundStatus")!=null) return refund(id,e,provider,count);
            if("IGNORED".equals(text(e,"status"))) return finish(id,"IGNORED",null);
            var found=resolveAttempt(provider,e);
            if(found.isEmpty()) return unresolved(id,count);
            var a=found.get();
            UUID orderId=jdbc.queryForObject("SELECT p.order_id FROM payments p JOIN payment_attempts a ON a.payment_id=p.id WHERE a.id=?",UUID.class,a.getId());
            var p=payments.findByOrderIdForUpdate(orderId).orElseThrow();
            entities.refresh(p); entities.refresh(a);
            if(p.getProvider()!=provider || a.getProvider()!=provider) return conflict(id,p,"PROVIDER_MISMATCH");
            UUID metadataPayment=uuid(e,"paymentId");
            if(metadataPayment!=null && !p.getId().equals(metadataPayment)) return conflict(id,p,"PAYMENT_METADATA_MISMATCH");
            if(conflicting(a.getIdempotencyKey(),text(e,"attemptIdempotencyKey"))) return conflict(id,p,"ATTEMPT_METADATA_MISMATCH");
            if(conflicting(a.getProviderChargeId(),text(e,"providerChargeId"))) return conflict(id,p,"PROVIDER_CHARGE_MISMATCH");
            if(conflicting(a.getProviderSessionId(),text(e,"providerSessionId"))
                    || conflicting(a.getProviderPaymentIntentId(),text(e,"providerPaymentIntentId"))) return conflict(id,p,"PROVIDER_IDENTIFIER_MISMATCH");
            if(e.hasNonNull("amount") && e.get("amount").decimalValue().compareTo(p.getAmount())!=0) return conflict(id,p,"AMOUNT_MISMATCH");
            if(text(e,"currency")!=null && !p.getCurrency().equalsIgnoreCase(text(e,"currency"))) return conflict(id,p,"CURRENCY_MISMATCH");
            String status=text(e,"status");
            if(!OPEN.contains(p.getStatus())) {
                if("SUCCESS".equals(status) && Set.of(PaymentStatus.EXPIRED,PaymentStatus.CANCELLED,PaymentStatus.FAILED).contains(p.getStatus()))
                    return conflict(id,p,"LATE_SUCCESS_REQUIRES_REVIEW");
                return finish(id,"IGNORED",p.getId());
            }
            if(a.getProviderSessionId()==null) a.setProviderSessionId(text(e,"providerSessionId"));
            if(a.getProviderPaymentIntentId()==null) a.setProviderPaymentIntentId(text(e,"providerPaymentIntentId"));
            if(a.getProviderChargeId()==null) a.setProviderChargeId(text(e,"providerChargeId"));
            PaymentStatus next=switch(status==null?"IGNORED":status) {
                case "SUCCESS" -> PaymentStatus.SUCCESS;
                case "FAILED" -> PaymentStatus.FAILED;
                case "EXPIRED" -> PaymentStatus.EXPIRED;
                case "CANCELLED" -> "checkout.session.expired".equals(text(e,"eventType"))?PaymentStatus.EXPIRED:PaymentStatus.CANCELLED;
                case "PROCESSING" -> PaymentStatus.PROCESSING;
                default -> null;
            };
            if(next==null) return finish(id,"IGNORED",p.getId());
            p.setStatus(next); a.setStatus(PaymentAttemptStatus.valueOf(next.name()));
            p.setFailureReason(null); a.setFailureReason(null);
            attempts.saveAndFlush(a); payments.save(p);
            switch(next) {
                case SUCCESS -> publisher.publishPaymentSuccess(p);
                case FAILED,CANCELLED -> publisher.publishPaymentFailed(p);
                case EXPIRED -> publisher.publishPaymentExpired(p);
                default -> { }
            }
            metrics.counter("payment.webhook.transition","status",next.name()).increment();
            log.info("payment_webhook_applied eventId={} paymentId={} orderId={} status={}",id,p.getId(),p.getOrderId(),next);
            return finish(id,"PROCESSED",p.getId());
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid retained envelope",ex); }
    }

    private WebhookProcessingStatus refund(UUID id,JsonNode e,PaymentProvider provider,int count) {
        var found=Optional.ofNullable(text(e,"providerRefundId")).flatMap(refunds::findByProviderRefundId);
        if(found.isEmpty()) return unresolved(id,count);
        UUID orderId=jdbc.queryForObject("SELECT p.order_id FROM payments p JOIN payment_refunds r ON r.payment_id=p.id WHERE r.id=?",UUID.class,found.get().getId());
        var p=payments.findByOrderIdForUpdate(orderId).orElseThrow();
        var r=refunds.findByIdForUpdate(found.get().getId()).orElseThrow();
        entities.refresh(p); entities.refresh(r);
        if(p.getProvider()!=provider) return conflict(id,p,"REFUND_PROVIDER_MISMATCH");
        if(e.hasNonNull("refundAmount") && e.get("refundAmount").decimalValue().compareTo(r.getAmount())!=0)
            return conflict(id,p,"REFUND_AMOUNT_MISMATCH");
        if (r.getStatus() == RefundStatus.REFUND_FAILED && "SUCCESS".equals(text(e, "refundStatus")))
            return conflict(id, p, "LATE_REFUND_SUCCESS_REQUIRES_REVIEW");
        if(r.getStatus()==RefundStatus.REFUNDED || r.getStatus()==RefundStatus.REFUND_FAILED) return finish(id,"IGNORED",p.getId());
        String status=text(e,"refundStatus");
        if("SUCCESS".equals(status)) r.setStatus(RefundStatus.REFUNDED);
        else if("FAILED".equals(status)) r.setStatus(RefundStatus.REFUND_FAILED);
        else if("PROCESSING".equals(status)) {
            // A nonterminal webhook cannot reset an exhausted retry/manual-review decision.
            if (r.getStatus() != RefundStatus.REFUND_MANUAL_REVIEW) r.setStatus(RefundStatus.REFUND_PROCESSING);
        }
        else return finish(id,"IGNORED",p.getId());
        if (r.getStatus() == RefundStatus.REFUNDED || r.getStatus() == RefundStatus.REFUND_FAILED) {
            r.setCompletedAt(java.time.Instant.now());
            r.setLeaseToken(null);
            r.setLeaseUntil(null);
            r.setFailureReason(r.getStatus() == RefundStatus.REFUND_FAILED ? "PAYMENT_REFUND_FAILED" : null);
        } else if (r.getStatus() != RefundStatus.REFUND_MANUAL_REVIEW) {
            r.setFailureReason(null);
        }
        refunds.saveAndFlush(r);
        var all=refunds.findByPayment_IdOrderByCreatedAtDesc(p.getId());
        BigDecimal total=all.stream().filter(f->f.getStatus()==RefundStatus.REFUNDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(p.getAmount())>0) throw new IllegalStateException("Refund conservation violation");
        boolean pending=all.stream().anyMatch(f->Set.of(RefundStatus.REFUND_REQUESTED,RefundStatus.REFUND_PROCESSING).contains(f.getStatus()));
        boolean review=all.stream().anyMatch(f->f.getStatus()==RefundStatus.REFUND_MANUAL_REVIEW);
        p.setStatus(total.compareTo(p.getAmount())==0?PaymentStatus.REFUNDED:pending?PaymentStatus.REFUND_PROCESSING:review?PaymentStatus.REFUND_FAILED:PaymentStatus.SUCCESS);
        p.setFailureReason(review ? "PAYMENT_REFUND_FAILED" : null);
        payments.save(p);
        if(r.getStatus()==RefundStatus.REFUNDED) publisher.publishRefundCompleted(p,r,total);
        else if(r.getStatus()==RefundStatus.REFUND_FAILED) publisher.publishRefundFailed(p,r);
        return finish(id,"PROCESSED",p.getId());
    }

    private Optional<PaymentAttempt> resolveAttempt(PaymentProvider provider,JsonNode e) {
        Optional<PaymentAttempt> a=Optional.empty();
        if(text(e,"providerSessionId")!=null) a=attempts.findByProviderAndProviderSessionId(provider,text(e,"providerSessionId"));
        if(a.isEmpty() && text(e,"providerPaymentIntentId")!=null) a=attempts.findByProviderAndProviderPaymentIntentId(provider,text(e,"providerPaymentIntentId"));
        if(a.isEmpty() && text(e,"providerChargeId")!=null) a=attempts.findByProviderAndProviderChargeId(provider,text(e,"providerChargeId"));
        if(a.isEmpty() && uuid(e,"paymentId")!=null && text(e,"attemptIdempotencyKey")!=null)
            a=attempts.findByPayment_IdAndIdempotencyKey(uuid(e,"paymentId"),text(e,"attemptIdempotencyKey"));
        return a;
    }
    private WebhookProcessingStatus finish(UUID id,String status,UUID paymentId) {
        jdbc.update("UPDATE payment_webhook_events SET processing_status=?,payment_id=?,processed_at=now(),attempts=attempts+1,last_error_code=NULL WHERE id=?",status,paymentId,id);
        return WebhookProcessingStatus.valueOf(status);
    }
    private WebhookProcessingStatus unresolved(UUID id,int count) {
        jdbc.update("UPDATE payment_webhook_events SET processing_status='FAILED',attempts=attempts+1,next_attempt_at=now()+(?*interval '1 second'),last_error_code='WEBHOOK_UNRESOLVED' WHERE id=?",Math.min(3600,2L<<Math.min(count,11)),id);
        metrics.counter("payment.webhook.unresolved").increment();
        log.warn("payment_webhook_unresolved eventId={} attempt={} terminal={}",id,count+1,count+1>=maxAttempts);
        return WebhookProcessingStatus.FAILED;
    }
    private WebhookProcessingStatus conflict(UUID id,Payment p,String code) {
        jdbc.update("UPDATE payment_webhook_events SET processing_status='FAILED',payment_id=?,attempts=?,last_error_code=? WHERE id=?",p.getId(),maxAttempts,code,id);
        metrics.counter("payment.webhook.state.conflict","code",code).increment();
        log.error("payment_webhook_manual_review eventId={} paymentId={} orderId={} code={}",id,p.getId(),p.getOrderId(),code);
        return WebhookProcessingStatus.FAILED;
    }
    private boolean conflicting(String saved,String incoming) { return saved!=null && incoming!=null && !saved.equals(incoming); }
    private String text(JsonNode e,String key) { return e.hasNonNull(key)?e.get(key).asText():null; }
    private UUID uuid(JsonNode e,String key) { return text(e,key)==null?null:UUID.fromString(text(e,key)); }
}
