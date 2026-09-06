package com.ecommerce.auth.repository;
import com.ecommerce.auth.entity.AuthOutboxEvent;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuthOutboxEventRepository extends JpaRepository<AuthOutboxEvent, UUID> {
  List<AuthOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
