package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentWebhookEvent;
import com.ecommerce.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    boolean existsByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    );

    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    );

    @Modifying
    @Query(value = """
            INSERT INTO payment_webhook_events (
                id,
                provider,
                provider_event_id,
                payment_id,
                event_type,
                processing_status,
                payload_hash,
                received_at,
                processed_at
            )
            VALUES (
                :id,
                :provider,
                :providerEventId,
                :paymentId,
                :eventType,
                :processingStatus,
                :payloadHash,
                :receivedAt,
                :processedAt
            )
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertReceivedEventIfAbsent(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("paymentId") UUID paymentId,
            @Param("eventType") String eventType,
            @Param("processingStatus") String processingStatus,
            @Param("payloadHash") String payloadHash,
            @Param("receivedAt") LocalDateTime receivedAt,
            @Param("processedAt") LocalDateTime processedAt
    );
}