package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable Order Service command requesting a full provider refund for a cancellation. */
public class PaymentRefundRequestedEvent extends AbstractDomainEvent {

  private UUID refundRequestId;
  private UUID paymentId;
  private UUID orderId;
  private UUID userId;
  private UUID requestedBy;
  private String actorType;
  private BigDecimal amount;
  private String currency;
  private String reason;

  public PaymentRefundRequestedEvent() {
    super(EventTypes.PAYMENT_REFUND_REQUESTED, EventSources.ORDER_SERVICE, null, null);
  }

  public PaymentRefundRequestedEvent(
      UUID refundRequestId,
      UUID paymentId,
      UUID orderId,
      UUID userId,
      UUID requestedBy,
      String actorType,
      BigDecimal amount,
      String currency,
      String reason,
      Instant requestedAt,
      String correlationId,
      String traceId) {
    super(EventTypes.PAYMENT_REFUND_REQUESTED, EventSources.ORDER_SERVICE, correlationId, traceId);
    setEventId(refundRequestId);
    setOccurredAt(requestedAt == null ? Instant.now() : requestedAt);
    this.refundRequestId = refundRequestId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.userId = userId;
    this.requestedBy = requestedBy;
    this.actorType = actorType;
    this.amount = amount;
    this.currency = currency;
    this.reason = reason;
  }

  public UUID getRefundRequestId() { return refundRequestId; }
  public void setRefundRequestId(UUID value) { refundRequestId = value; }
  public UUID getPaymentId() { return paymentId; }
  public void setPaymentId(UUID value) { paymentId = value; }
  public UUID getOrderId() { return orderId; }
  public void setOrderId(UUID value) { orderId = value; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID value) { userId = value; }
  public UUID getRequestedBy() { return requestedBy; }
  public void setRequestedBy(UUID value) { requestedBy = value; }
  public String getActorType() { return actorType; }
  public void setActorType(String value) { actorType = value; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal value) { amount = value; }
  public String getCurrency() { return currency; }
  public void setCurrency(String value) { currency = value; }
  public String getReason() { return reason; }
  public void setReason(String value) { reason = value; }
}
