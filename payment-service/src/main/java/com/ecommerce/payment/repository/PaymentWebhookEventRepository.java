package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentWebhookEvent;
import com.ecommerce.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

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
}