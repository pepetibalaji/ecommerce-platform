package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.AuthAuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, UUID> {

  List<AuthAuditEvent> findTop100BySubjectUserIdOrderByCreatedAtDesc(UUID subjectUserId);
}
