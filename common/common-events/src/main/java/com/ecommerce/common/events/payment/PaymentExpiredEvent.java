package com.ecommerce.common.events.payment;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PaymentExpiredEvent extends AbstractDomainEvent {
  private UUID paymentId;
  private UUID orderId;
  private UUID userId;
  private BigDecimal amount;
  private String currency;
  private String provider;

  public PaymentExpiredEvent() {
    super(EventTypes.PAYMENT_EXPIRED, EventSources.PAYMENT_SERVICE, null, null);
  }

  public PaymentExpiredEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount,
      String currency, String provider, String correlationId, String traceId) {
    super(EventTypes.PAYMENT_EXPIRED, EventSources.PAYMENT_SERVICE, correlationId, traceId);
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.provider = provider;
  }
}
