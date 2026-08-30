package com.ecommerce.notification.repository;

import com.ecommerce.notification.domain.NotificationRecipient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository
    extends JpaRepository<NotificationRecipient, UUID> {
  Optional<NotificationRecipient> findByUserIdAndActiveTrue(UUID userId);
}
