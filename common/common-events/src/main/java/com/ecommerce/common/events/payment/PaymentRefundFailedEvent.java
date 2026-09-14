package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Terminal refund execution failure; the order requires an operations decision. */
@Getter @Setter
public class PaymentRefundFailedEvent extends AbstractDomainEvent {
  private UUID refundId;
  private UUID refundRequestId;
  private UUID paymentId;
  private UUID orderId;
  private UUID userId;
  private BigDecimal amount;
  private String currency;
  private String provider;
  private String reason;

  public PaymentRefundFailedEvent() {
    super(EventTypes.PAYMENT_REFUND_FAILED, EventSources.PAYMENT_SERVICE, null, null);
  }

  public PaymentRefundFailedEvent(UUID refundId, UUID paymentId, UUID orderId, UUID userId,
      BigDecimal amount, String currency, String provider, String reason,
      String correlationId, String traceId) {
    super(EventTypes.PAYMENT_REFUND_FAILED, EventSources.PAYMENT_SERVICE, correlationId, traceId);
    this.refundId = refundId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.provider = provider;
    this.reason = reason;
  }
}
