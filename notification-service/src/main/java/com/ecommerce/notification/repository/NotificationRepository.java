package com.ecommerce.notification.repository;

import com.ecommerce.notification.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus status);

  List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID userId);
}
