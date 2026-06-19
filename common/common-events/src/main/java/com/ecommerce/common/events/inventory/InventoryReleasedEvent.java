package com.ecommerce.common.events.inventory;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.util.UUID;

public class InventoryReleasedEvent extends AbstractDomainEvent {

    private UUID orderId;
    private UUID productId;
    private int quantity;
    private String reason;

    public InventoryReleasedEvent() {
        super(
                EventTypes.INVENTORY_RELEASED,
                EventSources.INVENTORY_SERVICE,
                null,
                null
        );
    }

    public InventoryReleasedEvent(
            UUID orderId,
            UUID productId,
            int quantity,
            String reason,
            String correlationId,
            String traceId
    ) {
        super(
                EventTypes.INVENTORY_RELEASED,
                EventSources.INVENTORY_SERVICE,
                correlationId,
                traceId
        );
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.reason = reason;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }


    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}