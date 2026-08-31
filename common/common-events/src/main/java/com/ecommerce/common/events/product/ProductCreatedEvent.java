package com.ecommerce.common.events.product;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.util.UUID;

/** Contract for provisioning the product's zero-stock inventory record. */
public class ProductCreatedEvent extends AbstractDomainEvent {
  private UUID productId;
  private UUID sellerId;

  public ProductCreatedEvent() {
    super(EventTypes.PRODUCT_CREATED, EventSources.PRODUCT_SERVICE, null, null);
  }

  public ProductCreatedEvent(UUID productId, UUID sellerId, String correlationId) {
    super(EventTypes.PRODUCT_CREATED, EventSources.PRODUCT_SERVICE, correlationId, null);
    this.productId = productId;
    this.sellerId = sellerId;
  }

  public UUID getProductId() { return productId; }
  public void setProductId(UUID productId) { this.productId = productId; }
  public UUID getSellerId() { return sellerId; }
  public void setSellerId(UUID sellerId) { this.sellerId = sellerId; }
}
