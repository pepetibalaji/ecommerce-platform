package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.domain.NotificationStatus;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class AuthActionNotificationServiceTest {
  private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final AuthActionNotificationService service =
      new AuthActionNotificationService(new ObjectMapper(), processed, notifications);

  @Test
  void createsVerificationIntentWithoutPuttingAOneTimeTokenInThePayload() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();

    service.consume(
        AuthActionNotificationService.USER_VERIFICATION_REQUESTED,
        event(
            eventId,
            userId,
            "verificationActionId",
            actionId,
            "customer@example.test"));

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    Notification notification = saved.getValue();
    assertThat(notification.getType()).isEqualTo(AuthActionNotificationService.EMAIL_VERIFICATION);
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(notification.getPayload())
        .contains(actionId.toString(), "customer@example.test")
        .doesNotContain("token");
  }

  @Test
  void mapsEachAuthActionTopicToTheExpectedEmailType() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();

    service.consume(
        AuthActionNotificationService.PASSWORD_RESET_REQUESTED,
        event(eventId, userId, "passwordResetActionId", actionId, "customer@example.test"));
    service.consume(
        AuthActionNotificationService.EMAIL_CHANGE_REQUESTED,
        event(
            UUID.randomUUID(),
            userId,
            "emailChangeActionId",
            UUID.randomUUID(),
            "new-address@example.test"));

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications, times(2)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(Notification::getType)
        .containsExactly(
            AuthActionNotificationService.PASSWORD_RESET,
            AuthActionNotificationService.EMAIL_CHANGE_CONFIRMATION);
  }

  @Test
  void ignoresDuplicateEventsBeforeCreatingAnotherEmailIntent() {
    doThrow(new DataIntegrityViolationException("duplicate"))
        .when(processed)
        .saveAndFlush(any());

    service.consume(
        AuthActionNotificationService.PASSWORD_RESET_REQUESTED,
        event(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "passwordResetActionId",
            UUID.randomUUID(),
            "customer@example.test"));

    verifyNoInteractions(notifications);
  }

  @Test
  void rejectsAKafkaEventThatContainsARawToken() {
    assertThatThrownBy(
            () ->
                service.consume(
                    AuthActionNotificationService.USER_VERIFICATION_REQUESTED,
                    """
                    {"eventId":"%s","userId":"%s","email":"customer@example.test",
                     "verificationActionId":"%s","token":"must-not-be-here"}
                    """
                        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Auth identity action event");
    verifyNoInteractions(processed, notifications);
  }

  private String event(UUID eventId, UUID userId, String actionField, UUID actionId, String email) {
    return """
        {"eventId":"%s","userId":"%s","email":"%s","%s":"%s"}
        """
        .formatted(eventId, userId, email, actionField, actionId);
  }
}
