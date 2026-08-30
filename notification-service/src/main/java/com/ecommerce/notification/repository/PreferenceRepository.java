package com.ecommerce.notification.repository;

import com.ecommerce.notification.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
  Optional<NotificationPreference> findByUserIdAndChannelAndNotificationType(
      UUID userId, Channel channel, String notificationType);

  List<NotificationPreference> findByUserId(UUID userId);
}
