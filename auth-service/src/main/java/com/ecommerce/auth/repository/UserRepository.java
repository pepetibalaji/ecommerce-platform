package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByEmailNormalized(String emailNormalized);

  @Query(
      "select distinct u from User u left join fetch u.roles r left join fetch r.permissions "
          + "where u.emailNormalized = :emailNormalized")
  Optional<User> findAuthorizationDataByEmailNormalized(
      @Param("emailNormalized") String emailNormalized);

  boolean existsByEmail(String email);

  boolean existsByEmailNormalized(String emailNormalized);

  List<User> findByStatus(UserStatus status);
}
