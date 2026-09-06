package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecommerce.notification.config.AuthActionDeliveryProperties;
import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.provider.EmailProvider;
import com.ecommerce.notification.repository.DeliveryRepository;
import com.ecommerce.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationDeliveryServiceAuthActionTest {
  @Test
  void fetchesTheDeliveryTokenOnlyAtSendTimeAndDoesNotPersistIt() throws Exception {
    NotificationRepository notifications = mock(NotificationRepository.class);
    DeliveryRepository deliveries = mock(DeliveryRepository.class);
    RecipientDirectoryService recipients = mock(RecipientDirectoryService.class);
    EmailProvider provider = mock(EmailProvider.class);
    AuthDeliveryTokenClient tokens = mock(AuthDeliveryTokenClient.class);
    NotificationProperties notificationProperties = new NotificationProperties();
    AuthActionDeliveryProperties actionProperties = new AuthActionDeliveryProperties();
    actionProperties.setVerificationUrl("https://shop.example.test/verify-email?source=email");

    UUID notificationId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    Notification notification = new Notification();
    notification.setId(notificationId);
    notification.setEventId(UUID.randomUUID());
    notification.setRecipientUserId(UUID.randomUUID());
    notification.setChannel(Channel.EMAIL);
    notification.setType(AuthActionNotificationService.EMAIL_VERIFICATION);
    notification.setPayload(
        "{\"actionId\":\""
            + actionId
            + "\",\"deliveryEmail\":\"customer@example.test\",\"eventType\":\"auth.user-verification-requested.v1\"}");
    notification.setStatus(NotificationStatus.PENDING);

    when(notifications.findById(notificationId)).thenReturn(Optional.of(notification));
    when(deliveries.findTopByNotificationIdOrderByAttemptCountDesc(notificationId))
        .thenReturn(Optional.empty());
    when(tokens.obtainDeliveryToken(actionId)).thenReturn("one-time-secret");
    when(provider.send(eq("customer@example.test"), any(Notification.class))).thenReturn("provider-id");

    new NotificationDeliveryService(
            notifications,
            deliveries,
            recipients,
            provider,
            notificationProperties,
            tokens,
            actionProperties,
            new ObjectMapper(),
            new SimpleMeterRegistry())
        .deliver(notificationId);

    verify(tokens).obtainDeliveryToken(actionId);
    verifyNoInteractions(recipients);
    ArgumentCaptor<Notification> outbound = ArgumentCaptor.forClass(Notification.class);
    verify(provider).send(eq("customer@example.test"), outbound.capture());
    assertThat(outbound.getValue().getPayload())
        .contains("https://shop.example.test/verify-email?source=email&token=one-time-secret")
        .contains("one-time-secret");
    assertThat(notification.getPayload()).doesNotContain("one-time-secret", "actionUrl");
    verify(notifications).save(same(notification));
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
  }
}
