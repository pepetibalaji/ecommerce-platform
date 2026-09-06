package com.ecommerce.auth.repository;
import com.ecommerce.auth.entity.RefreshSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
  Optional<RefreshSession> findByTokenHash(String tokenHash);
  List<RefreshSession> findByUser_IdAndRevokedAtIsNull(UUID userId);
  List<RefreshSession> findByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);
}
