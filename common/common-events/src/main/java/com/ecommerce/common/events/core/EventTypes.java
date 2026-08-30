package com.ecommerce.common.events.core;

public final class EventTypes {

  private EventTypes() {}

  public static final String ORDER_CREATED = "ORDER_CREATED";
  public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
  public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

  public static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
  public static final String INVENTORY_RELEASED = "INVENTORY_RELEASED";

  public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
  public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
  public static final String PAYMENT_REFUND_COMPLETED = "PAYMENT_REFUND_COMPLETED";

  public static final String SHIPMENT_CREATED = "SHIPMENT_CREATED";

  public static final String NOTIFICATION_REQUESTED = "NOTIFICATION_REQUESTED";

  public static final String DEAD_LETTER = "DEAD_LETTER";
}
