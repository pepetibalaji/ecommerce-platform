package com.ecommerce.notification.kafka;

import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.notification.service.NotificationEventService;
import com.ecommerce.notification.service.RecipientDirectoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaConsumer {
  private final NotificationEventService events;
  private final RecipientDirectoryService recipients;

  public NotificationKafkaConsumer(
      NotificationEventService events, RecipientDirectoryService recipients) {
    this.events = events;
    this.recipients = recipients;
  }

  @KafkaListener(
      topics = {
        KafkaTopics.ORDER_CREATED,
        KafkaTopics.PAYMENT_SUCCESS,
        KafkaTopics.PAYMENT_FAILED,
        KafkaTopics.ORDER_CANCELLED,
        KafkaTopics.PAYMENT_REFUND_COMPLETED,
        KafkaTopics.ORDER_SHIPPED,
        KafkaTopics.ORDER_DELIVERED,
        KafkaTopics.LOW_INVENTORY,
        KafkaTopics.SELLER_ORDER_PAID
      },
      groupId = "${notification.kafka.consumer-group:notification-service}")
  public void consume(
      String message, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
    events.consume(record.topic(), message);
  }

  @KafkaListener(
      topics = KafkaTopics.USER_CONTACT_UPDATED,
      groupId = "${notification.kafka.consumer-group:notification-service}")
  public void consumeUserContact(String message) {
    recipients.consume(message);
  }
}
