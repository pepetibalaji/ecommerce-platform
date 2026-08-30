package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByToken(String token);

  List<RefreshToken> findAllByUser_Id(UUID userId);

  void deleteAllByUser_Id(UUID userId);
}
