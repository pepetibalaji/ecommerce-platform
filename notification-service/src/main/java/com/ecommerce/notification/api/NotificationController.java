package com.ecommerce.notification.api;

import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationRepository notifications;
  private final DeliveryRepository deliveries;
  private final PreferenceRepository preferences;

  @GetMapping("/users/{userId}")
  @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
  public List<Notification> history(@PathVariable UUID userId) {
    return notifications.findByRecipientUserIdOrderByCreatedAtDesc(userId);
  }

  @GetMapping("/users/{userId}/preferences")
  @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
  public List<NotificationPreference> preferences(@PathVariable UUID userId) {
    return preferences.findByUserId(userId);
  }

  @PutMapping("/users/{userId}/preferences")
  @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
  public NotificationPreference savePreference(
      @PathVariable UUID userId, @Valid @RequestBody PreferenceRequest request) {
    NotificationPreference p =
        preferences
            .findByUserIdAndChannelAndNotificationType(
                userId, request.channel(), request.notificationType())
            .orElseGet(NotificationPreference::new);
    p.setUserId(userId);
    p.setChannel(request.channel());
    p.setNotificationType(request.notificationType());
    p.setEnabled(request.enabled());
    return preferences.save(p);
  }

  @GetMapping("/admin/failed")
  @PreAuthorize("hasRole('ADMIN')")
  public List<Notification> failed() {
    return notifications.findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus.FAILED);
  }

  @GetMapping("/admin/{notificationId}/deliveries")
  @PreAuthorize("hasRole('ADMIN')")
  public List<NotificationDelivery> deliveries(@PathVariable UUID notificationId) {
    return deliveries.findByNotificationIdOrderByAttemptCountDesc(notificationId);
  }

  public record PreferenceRequest(
      Channel channel, @NotBlank String notificationType, boolean enabled) {}
}
