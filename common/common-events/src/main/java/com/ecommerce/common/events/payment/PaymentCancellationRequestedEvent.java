package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Payment serializes cancellation against verified outcomes before Order releases stock. */
@Getter @Setter
public class PaymentCancellationRequestedEvent extends AbstractDomainEvent {
  private UUID cancellationRequestId;
  private UUID orderId;
  private UUID userId;
  private UUID requestedBy;
  private String actorType;
  private BigDecimal amount;
  private String currency;
  private String reason;
  private boolean expiryRequested;

  public PaymentCancellationRequestedEvent() {
    super(EventTypes.PAYMENT_CANCELLATION_REQUESTED, EventSources.ORDER_SERVICE, null, null);
  }

  public PaymentCancellationRequestedEvent(UUID cancellationRequestId, UUID orderId, UUID userId,
      UUID requestedBy, String actorType, BigDecimal amount, String currency, String reason,
      Instant requestedAt, String correlationId, String traceId) {
    super(EventTypes.PAYMENT_CANCELLATION_REQUESTED, EventSources.ORDER_SERVICE, correlationId, traceId);
    setEventId(cancellationRequestId);
    setOccurredAt(requestedAt);
    this.cancellationRequestId = cancellationRequestId;
    this.orderId = orderId;
    this.userId = userId;
    this.requestedBy = requestedBy;
    this.actorType = actorType;
    this.amount = amount;
    this.currency = currency;
    this.reason = reason;
  }
}
