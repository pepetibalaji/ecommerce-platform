package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationEventServiceTest {
  @Test
  void createsOnePendingEmailIntentFromOrderCreated() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    when(preferences.findByUserIdAndChannelAndNotificationType(
            any(), eq(Channel.EMAIL), eq("ORDER_RECEIVED")))
        .thenReturn(Optional.empty());
    UUID eventId = UUID.randomUUID(), userId = UUID.randomUUID();
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences)
        .consume(
            "order-created",
            "{\"eventId\":\""
                + eventId
                + "\",\"userId\":\""
                + userId
                + "\",\"orderId\":\""
                + UUID.randomUUID()
                + "\"}");
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
    assertThat(saved.getValue().getRecipientUserId()).isEqualTo(userId);
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
  }

  @Test
  void ignoresDuplicateKafkaEvent() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    PreferenceRepository preferences = mock(PreferenceRepository.class);
    doThrow(new DataIntegrityViolationException("duplicate")).when(processed).saveAndFlush(any());
    new NotificationEventService(new ObjectMapper(), processed, notifications, preferences)
        .consume(
            "order-created",
            "{\"eventId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\"}");
    verifyNoInteractions(notifications, preferences);
  }
}
