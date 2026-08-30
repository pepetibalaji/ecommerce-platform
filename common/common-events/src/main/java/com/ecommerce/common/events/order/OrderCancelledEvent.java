package com.ecommerce.common.events.order;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.util.UUID;

public class OrderCancelledEvent extends AbstractDomainEvent {

  private UUID orderId;
  private UUID userId;
  private String reason;

  public OrderCancelledEvent() {
    super(EventTypes.ORDER_CANCELLED, EventSources.ORDER_SERVICE, null, null);
  }

  public OrderCancelledEvent(
      UUID orderId, UUID userId, String reason, String correlationId, String traceId) {
    super(EventTypes.ORDER_CANCELLED, EventSources.ORDER_SERVICE, correlationId, traceId);
    this.orderId = orderId;
    this.userId = userId;
    this.reason = reason;
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

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
