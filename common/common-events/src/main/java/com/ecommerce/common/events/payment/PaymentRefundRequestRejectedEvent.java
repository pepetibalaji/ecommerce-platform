package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;

import java.util.UUID;

/** Payment Service's terminal business rejection of an Order refund-request command. */
public class PaymentRefundRequestRejectedEvent extends AbstractDomainEvent {

  private UUID refundRequestId;
  private UUID paymentId;
  private UUID orderId;
  private String reason;

  public PaymentRefundRequestRejectedEvent() {
    super(EventTypes.PAYMENT_REFUND_REQUEST_REJECTED, EventSources.PAYMENT_SERVICE, null, null);
  }

  public PaymentRefundRequestRejectedEvent(
      UUID refundRequestId,
      UUID paymentId,
      UUID orderId,
      String reason,
      String correlationId,
      String traceId) {
    super(EventTypes.PAYMENT_REFUND_REQUEST_REJECTED, EventSources.PAYMENT_SERVICE, correlationId, traceId);
    // The command id is stable across retries, allowing Order Service to deduplicate rejection.
    setEventId(refundRequestId);
    this.refundRequestId = refundRequestId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.reason = reason;
  }

  public UUID getRefundRequestId() { return refundRequestId; }
  public void setRefundRequestId(UUID value) { refundRequestId = value; }
  public UUID getPaymentId() { return paymentId; }
  public void setPaymentId(UUID value) { paymentId = value; }
  public UUID getOrderId() { return orderId; }
  public void setOrderId(UUID value) { orderId = value; }
  public String getReason() { return reason; }
  public void setReason(String value) { reason = value; }
}
