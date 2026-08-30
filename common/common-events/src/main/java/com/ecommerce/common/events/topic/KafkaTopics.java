package com.ecommerce.common.events.topic;

import java.util.List;

public final class KafkaTopics {

  private KafkaTopics() {}

  public static final String ORDER_CREATED = "order-created";
  public static final String INVENTORY_RESERVED = "inventory-reserved";
  public static final String INVENTORY_RELEASED = "inventory-released";
  public static final String PAYMENT_SUCCESS = "payment-success";
  public static final String PAYMENT_FAILED = "payment-failed";
  public static final String PAYMENT_REFUND_COMPLETED = "payment-refund-completed";
  public static final String ORDER_COMPLETED = "order-completed";
  public static final String ORDER_CANCELLED = "order-cancelled";
  public static final String SHIPMENT_CREATED = "shipment-created";
  public static final String ORDER_SHIPPED = "order-shipped";
  public static final String ORDER_DELIVERED = "order-delivered";
  public static final String LOW_INVENTORY = "low-inventory";
  public static final String SELLER_ORDER_PAID = "seller-order-paid";
  public static final String USER_CONTACT_UPDATED = "user-contact-updated";
  public static final String NOTIFICATION_REQUESTED = "notification-requested";

  public static final String ORDER_DLQ = "order-dlq";
  public static final String INVENTORY_DLQ = "inventory-dlq";
  public static final String PAYMENT_DLQ = "payment-dlq";
  public static final String NOTIFICATION_DLQ = "notification-dlq";

  public static List<String> businessTopics() {
    return List.of(
        ORDER_CREATED,
        INVENTORY_RESERVED,
        INVENTORY_RELEASED,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        PAYMENT_REFUND_COMPLETED,
        ORDER_COMPLETED,
        ORDER_CANCELLED,
        SHIPMENT_CREATED,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        LOW_INVENTORY,
        SELLER_ORDER_PAID,
        USER_CONTACT_UPDATED,
        NOTIFICATION_REQUESTED);
  }

  public static List<String> deadLetterTopics() {
    return List.of(ORDER_DLQ, INVENTORY_DLQ, PAYMENT_DLQ, NOTIFICATION_DLQ);
  }
}
