package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.math.BigDecimal;
import java.util.UUID;

/** A provider-confirmed refund. eventId is the refund id, so republishes are idempotent. */
public class PaymentRefundCompletedEvent extends AbstractDomainEvent {
  private UUID refundId;
  private UUID paymentId;
  private UUID orderId;
  private UUID userId;
  private BigDecimal amount;
  private BigDecimal totalRefundedAmount;
  private BigDecimal paymentAmount;
  private String currency;
  private boolean fullRefund;

  public PaymentRefundCompletedEvent() {
    super(EventTypes.PAYMENT_REFUND_COMPLETED, EventSources.PAYMENT_SERVICE, null, null);
  }

  public PaymentRefundCompletedEvent(
      UUID refundId,
      UUID paymentId,
      UUID orderId,
      UUID userId,
      BigDecimal amount,
      BigDecimal totalRefundedAmount,
      BigDecimal paymentAmount,
      String currency,
      String correlationId,
      String traceId) {
    super(
        EventTypes.PAYMENT_REFUND_COMPLETED, EventSources.PAYMENT_SERVICE, correlationId, traceId);
    setEventId(refundId);
    this.refundId = refundId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.totalRefundedAmount = totalRefundedAmount;
    this.paymentAmount = paymentAmount;
    this.currency = currency;
    this.fullRefund = totalRefundedAmount.compareTo(paymentAmount) == 0;
  }

  public UUID getRefundId() {
    return refundId;
  }

  public void setRefundId(UUID v) {
    refundId = v;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(UUID v) {
    paymentId = v;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public void setOrderId(UUID v) {
    orderId = v;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID v) {
    userId = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }

  public BigDecimal getTotalRefundedAmount() {
    return totalRefundedAmount;
  }

  public void setTotalRefundedAmount(BigDecimal v) {
    totalRefundedAmount = v;
  }

  public BigDecimal getPaymentAmount() {
    return paymentAmount;
  }

  public void setPaymentAmount(BigDecimal v) {
    paymentAmount = v;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String v) {
    currency = v;
  }

  public boolean isFullRefund() {
    return fullRefund;
  }

  public void setFullRefund(boolean v) {
    fullRefund = v;
  }
}
