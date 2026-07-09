package com.ecommerce.common.events.order;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent extends AbstractDomainEvent {

    private UUID orderId;

    private UUID userId;

    private BigDecimal totalAmount;

    private String currency;

    private List<OrderItemEvent> items = new ArrayList<>();

    public OrderCreatedEvent() {
        super(
                EventTypes.ORDER_CREATED,
                EventSources.ORDER_SERVICE,
                null,
                null
        );
    }

    public OrderCreatedEvent(
            UUID orderId,
            UUID userId,
            BigDecimal totalAmount,
            String currency,
            List<OrderItemEvent> items,
            String correlationId,
            String traceId
    ) {
        super(
                EventTypes.ORDER_CREATED,
                EventSources.ORDER_SERVICE,
                correlationId,
                traceId
        );
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.currency = normalizeCurrency(currency);
        this.items = items == null ? new ArrayList<>() : items;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = normalizeCurrency(currency);
    }

    public List<OrderItemEvent> getItems() {
        return items;
    }

    public void setItems(List<OrderItemEvent> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }

        return currency.trim().toUpperCase();
    }
}