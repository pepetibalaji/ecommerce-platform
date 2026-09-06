package com.ecommerce.auth.service;

import com.ecommerce.auth.config.BootstrapAdminProperties;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Creates the first administrator only when its password is supplied by the deployment. */
@Service
@RequiredArgsConstructor
public class BootstrapAdminService {
  private final BootstrapAdminProperties properties;
  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public void bootstrap() {
    if (!StringUtils.hasText(properties.getEmail()) || !StringUtils.hasText(properties.getPassword())) return;

    String email = properties.getEmail().trim();
    String normalizedEmail = email.toLowerCase(Locale.ROOT);
    if (users.existsByEmailNormalized(normalizedEmail)) return;

    Role admin = roles.findByCode("ADMIN")
        .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
    User user = User.builder()
        .name("Platform Administrator")
        .email(email)
        .emailNormalized(normalizedEmail)
        .passwordHash(passwordEncoder.encode(properties.getPassword()))
        .status(UserStatus.ACTIVE)
        .emailVerifiedAt(Instant.now())
        .build();
    user.getRoles().add(admin);
    users.save(user);
  }
}
