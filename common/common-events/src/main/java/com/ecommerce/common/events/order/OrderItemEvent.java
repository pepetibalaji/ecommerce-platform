package com.ecommerce.common.events.order;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemEvent {

  private UUID productId;
  private int quantity;
  private BigDecimal unitPrice;
  private BigDecimal lineTotal;

  public OrderItemEvent() {}

  public OrderItemEvent(UUID productId, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    this.productId = productId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.lineTotal = lineTotal;
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

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public void setLineTotal(BigDecimal lineTotal) {
    this.lineTotal = lineTotal;
  }
}
