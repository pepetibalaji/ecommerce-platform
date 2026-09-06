package com.ecommerce.auth.repository;
import com.ecommerce.auth.entity.IdentityActionToken;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface IdentityActionTokenRepository extends JpaRepository<IdentityActionToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<IdentityActionToken> findByTokenHash(String tokenHash);

  List<IdentityActionToken> findByUser_IdAndActionTypeAndConsumedAtIsNull(
      UUID userId, com.ecommerce.auth.entity.enums.IdentityActionType actionType);
}
