package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  List<User> findByStatus(UserStatus status);
}
