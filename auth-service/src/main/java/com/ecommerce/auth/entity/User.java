package com.ecommerce.auth.entity;

import com.ecommerce.auth.entity.enums.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "users",
    indexes = {
      @Index(name = "idx_users_status", columnList = "status")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id private UUID id;

  @Column(name = "display_name", nullable = false)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column(name = "email_normalized", nullable = false, unique = true)
  private String emailNormalized;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(name = "email_verified_at") private Instant emailVerifiedAt;
  @Column(name = "password_changed_at") private Instant passwordChangedAt;
  @Column(name = "token_version", nullable = false) private Long tokenVersion;

  @Column(name = "created_at", nullable = false) private Instant createdAt;

  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "deleted_at") private Instant deletedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Builder.Default
  private Set<Role> roles = new HashSet<>();

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
    if (status == null) {
      status = UserStatus.PENDING_VERIFICATION;
    }
    if (tokenVersion == null) {
      tokenVersion = 0L;
    }
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isActiveAndVerified() {
    return status == UserStatus.ACTIVE && emailVerifiedAt != null;
  }

  /** Stable role order prevents a HashSet iteration order from changing the legacy role claim. */
  public List<String> getRoleCodes() {
    return safeRoles().stream()
        .map(Role::getCode)
        .filter(code -> code != null && !code.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  /** Permissions are flattened for resource-server authorization without exposing database ids. */
  public List<String> getPermissionCodes() {
    return safeRoles().stream()
        .flatMap(role -> role.getPermissions().stream())
        .map(Permission::getCode)
        .filter(code -> code != null && !code.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  public String getPrimaryRoleCode() {
    List<String> codes = getRoleCodes();
    for (String preferred : List.of("ADMIN", "SELLER", "CUSTOMER")) {
      if (codes.contains(preferred)) {
        return preferred;
      }
    }
    return codes.isEmpty() ? "CUSTOMER" : codes.getFirst();
  }

  private Set<Role> safeRoles() {
    return roles == null ? Set.of() : roles;
  }

  /** @deprecated Compatibility bridge while callers migrate to the RBAC set. */
  @Deprecated
  public com.ecommerce.auth.entity.enums.Role getRole() {
    try {
      return com.ecommerce.auth.entity.enums.Role.valueOf(getPrimaryRoleCode());
    } catch (IllegalArgumentException ignored) {
      return com.ecommerce.auth.entity.enums.Role.CUSTOMER;
    }
  }

  /** @deprecated Password is always a hash; use passwordHash in new code. */
  @Deprecated
  public String getPassword() { return passwordHash; }

  @Deprecated
  public void setPassword(String password) { this.passwordHash = password; }

  /** @deprecated Compatibility bridge while RBAC services are introduced. */
  @Deprecated
  public void setRole(com.ecommerce.auth.entity.enums.Role role) {
    Role mapped = new Role();
    mapped.setCode(role.name());
    this.roles = new HashSet<>(Set.of(mapped));
  }
}
