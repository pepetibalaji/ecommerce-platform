package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationEventServiceTest {
  @Test
  void createsOneConfirmationOnlyFromCompletedOrder() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    when(preferences.findByUserIdAndChannelAndNotificationType(
            any(), eq(Channel.EMAIL), eq("ORDER_RECEIVED")))
        .thenReturn(Optional.empty());
    UUID eventId = UUID.randomUUID(), userId = UUID.randomUUID();
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences,
        new NotificationProperties())
        .consume(
            "order-completed",
            "{\"eventId\":\""
                + eventId
                + "\",\"userId\":\""
                + userId
                + "\",\"orderId\":\""
                + UUID.randomUUID()
                + "\",\"paymentId\":\"" + UUID.randomUUID()
                + "\",\"totalAmount\":1499,\"currency\":\"INR\"}");
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
    assertThat(saved.getValue().getRecipientUserId()).isEqualTo(userId);
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(saved.getValue().getType()).isEqualTo("ORDER_RECEIVED");
    assertThat(saved.getValue().getPayload())
        .contains("order-completed", "paymentId", "1499", "INR");
  }

  @Test
  void doesNotCreateCustomerEmailBeforeOrderIsConfirmed() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    NotificationEventService service = new NotificationEventService(
        new ObjectMapper(), processed, notifications, preferences, new NotificationProperties());
    String event = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"userId\":\""
        + UUID.randomUUID() + "\",\"orderId\":\"" + UUID.randomUUID() + "\"}";

    service.consume("order-created", event);
    service.consume("payment-success", event);

    verifyNoInteractions(notifications, preferences);
  }

  @Test
  void reportsCancelledPaymentAsOrderCancellationInsteadOfPaymentFailure() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences,
        new NotificationProperties())
        .consume("payment-failed", "{\"eventId\":\"" + UUID.randomUUID()
            + "\",\"userId\":\"" + UUID.randomUUID()
            + "\",\"orderId\":\"" + UUID.randomUUID()
            + "\",\"failureCode\":\"PAYMENT_CANCELLED\"}");

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getType()).isEqualTo("ORDER_CANCELLED");
  }

  @Test
  void ignoresDuplicateKafkaEvent() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    doThrow(new DataIntegrityViolationException("duplicate")).when(processed).saveAndFlush(any());
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences,
        new NotificationProperties())
        .consume(
            "order-completed",
            "{\"eventId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\"}");
    verifyNoInteractions(notifications, preferences);
  }

  @Test
  void keepsStockTemplateFieldsWithoutPersistingRecipientDetails() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    UUID sellerId = UUID.randomUUID();
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences,
        new NotificationProperties())
        .consume(
            "low-inventory",
            "{\"eventId\":\"" + UUID.randomUUID() + "\",\"sellerUserId\":\"" + sellerId
                + "\",\"productId\":\"product-123\",\"availableStock\":3}");

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getType()).isEqualTo("LOW_STOCK_WARNING");
    assertThat(saved.getValue().getPayload())
        .contains("product-123", "availableStock")
        .doesNotContain("sellerUserId", sellerId.toString());
  }

  @Test
  void suppressesOutOfStockEmailIntentByDefault() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences,
        new NotificationProperties())
        .consume(
            "out-of-stock",
            "{\"eventId\":\"" + UUID.randomUUID() + "\",\"sellerUserId\":\""
                + UUID.randomUUID() + "\",\"productId\":\"product-123\",\"availableStock\":0}");

    verify(processed).saveAndFlush(any(ProcessedEvent.class));
    verifyNoInteractions(notifications, preferences);
  }
}
