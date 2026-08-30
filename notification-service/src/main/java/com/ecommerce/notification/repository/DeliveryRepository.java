package com.ecommerce.notification.repository;

import com.ecommerce.notification.domain.NotificationDelivery;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
  Optional<NotificationDelivery> findTopByNotificationIdOrderByAttemptCountDesc(
      UUID notificationId);

  List<NotificationDelivery> findByNotificationIdOrderByAttemptCountDesc(UUID notificationId);
}
