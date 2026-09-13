package com.ecommerce.common.events.order;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

public class OrderCompletedEvent extends AbstractDomainEvent {

  private UUID orderId;
  private UUID userId;
  private UUID paymentId;
  private BigDecimal totalAmount;
  private List<OrderItemEvent> items = List.of();

  public OrderCompletedEvent() {
    super(EventTypes.ORDER_COMPLETED, EventSources.ORDER_SERVICE, null, null);
  }

  public OrderCompletedEvent(
      UUID orderId,
      UUID userId,
      UUID paymentId,
      BigDecimal totalAmount,
      String correlationId,
      String traceId) {
    this(orderId, userId, paymentId, totalAmount, List.of(), correlationId, traceId);
  }

  public OrderCompletedEvent(UUID orderId, UUID userId, UUID paymentId, BigDecimal totalAmount,
      List<OrderItemEvent> items, String correlationId, String traceId) {
    super(EventTypes.ORDER_COMPLETED, EventSources.ORDER_SERVICE, correlationId, traceId);
    this.orderId = orderId;
    this.userId = userId;
    this.paymentId = paymentId;
    this.totalAmount = totalAmount;
    this.items = items == null ? List.of() : List.copyOf(items);
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

  public UUID getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(UUID paymentId) {
    this.paymentId = paymentId;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }
  public List<OrderItemEvent> getItems() { return items; }
  public void setItems(List<OrderItemEvent> items) { this.items = items == null ? List.of() : List.copyOf(items); }
}
