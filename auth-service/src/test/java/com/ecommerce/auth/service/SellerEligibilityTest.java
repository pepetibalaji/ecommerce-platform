package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerEligibilityTest {
  private static final UUID SELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @Mock private UserRepository users;
  @InjectMocks private AccountService accounts;

  @Test
  void verifiedActiveMultiRoleSellerIsEligible() {
    User user = seller();
    user.getRoles().add(role("CUSTOMER"));
    user.getRoles().add(role("ADMIN"));
    when(users.findById(SELLER_ID)).thenReturn(Optional.of(user));
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = UserStatus.class, names = {"PENDING_VERIFICATION", "SUSPENDED", "DELETED"})
  void inactiveSellerIsIneligible(UserStatus status) {
    User user = seller();
    user.setStatus(status);
    when(users.findById(SELLER_ID)).thenReturn(Optional.of(user));
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isFalse();
  }

  @Test
  void missingSellerIsIneligible() {
    when(users.findById(SELLER_ID)).thenReturn(Optional.empty());
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isFalse();
  }

  @Test
  void unverifiedSellerIsIneligibleEvenWhenStatusIsActive() {
    User user = seller();
    user.setEmailVerifiedAt(null);
    when(users.findById(SELLER_ID)).thenReturn(Optional.of(user));
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isFalse();
  }

  @Test
  void deletedTimestampMakesAnOtherwiseActiveSellerIneligible() {
    User user = seller();
    user.setDeletedAt(Instant.now());
    when(users.findById(SELLER_ID)).thenReturn(Optional.of(user));
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isFalse();
  }

  @Test
  void adminWithoutSellerRoleIsIneligible() {
    User user = seller();
    user.setRoles(Set.of(role("ADMIN"), role("CUSTOMER")));
    when(users.findById(SELLER_ID)).thenReturn(Optional.of(user));
    assertThat(accounts.isEligibleSeller(SELLER_ID)).isFalse();
  }

  private User seller() {
    return User.builder()
        .id(SELLER_ID)
        .status(UserStatus.ACTIVE)
        .emailVerifiedAt(Instant.now())
        .roles(new java.util.HashSet<>(Set.of(role("SELLER"))))
        .build();
  }

  private Role role(String code) {
    Role role = new Role();
    role.setCode(code);
    return role;
  }
}
