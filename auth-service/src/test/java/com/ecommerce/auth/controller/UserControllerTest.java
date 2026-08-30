package com.ecommerce.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private UserService userService;

  @InjectMocks private UserController userController;

  @Test
  void shouldGetMe() {

    UserProfileResponse response = mock(UserProfileResponse.class);

    when(userService.getMe(USER_ID)).thenReturn(response);

    UserProfileResponse result = userController.getMe(jwt());

    assertThat(result).isSameAs(response);

    verify(userService).getMe(USER_ID);
  }

  @Test
  void shouldUpdateMe() {

    UpdateMeRequest request = new UpdateMeRequest();

    UserProfileResponse response = mock(UserProfileResponse.class);

    when(userService.updateMe(USER_ID, request)).thenReturn(response);

    UserProfileResponse result = userController.updateMe(jwt(), request);

    assertThat(result).isSameAs(response);

    verify(userService).updateMe(USER_ID, request);
  }

  @Test
  void shouldDeleteMe() {

    userController.deleteMe(jwt());

    verify(userService).deleteMe(USER_ID);
  }

  private Jwt jwt() {

    Instant now = Instant.now();

    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer("http://localhost:8081")
        .subject("test@example.com")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600))
        .claim("userId", USER_ID.toString())
        .claim("role", "CUSTOMER")
        .claim("status", "ACTIVE")
        .claim("tokenVersion", 0)
        .build();
  }
}
