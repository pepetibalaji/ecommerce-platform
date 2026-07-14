package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentSuccessEvent extends AbstractDomainEvent {

    private UUID paymentId;

    private UUID orderId;

    private UUID userId;

    private BigDecimal amount;

    private String currency;

    private String provider;

    private String transactionId;

    public PaymentSuccessEvent() {
        super(
                EventTypes.PAYMENT_SUCCESS,
                EventSources.PAYMENT_SERVICE,
                null,
                null
        );
    }

    public PaymentSuccessEvent(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String provider,
            String transactionId,
            String correlationId,
            String traceId
    ) {
        super(
                EventTypes.PAYMENT_SUCCESS,
                EventSources.PAYMENT_SERVICE,
                correlationId,
                traceId
        );
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.currency = normalizeCurrency(currency);
        this.provider = normalizeProvider(provider);
        this.transactionId = transactionId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = normalizeCurrency(currency);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = normalizeProvider(provider);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return currency;
        }

        return currency.trim().toUpperCase();
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return provider;
        }

        return provider.trim().toUpperCase();
    }
}