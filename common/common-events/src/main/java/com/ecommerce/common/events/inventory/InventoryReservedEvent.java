package com.ecommerce.common.events.inventory;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.util.UUID;

public class InventoryReservedEvent extends AbstractDomainEvent {

  private UUID reservationId;
  private UUID orderId;
  private UUID productId;
  private int quantity;

  public InventoryReservedEvent() {
    super(EventTypes.INVENTORY_RESERVED, EventSources.INVENTORY_SERVICE, null, null);
  }

  public InventoryReservedEvent(
      UUID reservationId,
      UUID orderId,
      UUID productId,
      int quantity,
      String correlationId,
      String traceId) {
    super(EventTypes.INVENTORY_RESERVED, EventSources.INVENTORY_SERVICE, correlationId, traceId);
    this.reservationId = reservationId;
    this.orderId = orderId;
    this.productId = productId;
    this.quantity = quantity;
  }

  public UUID getReservationId() {
    return reservationId;
  }

  public void setReservationId(UUID reservationId) {
    this.reservationId = reservationId;
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
}
