package com.ecommerce.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.service.ActionTokenService;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.service.BrowserRefreshCookieService;
import com.ecommerce.auth.service.RegistrationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private RegistrationService registrationService;
  @Mock private ActionTokenService actionTokenService;
  @Mock private BrowserRefreshCookieService refreshCookies;
  @InjectMocks private AuthController controller;

  @Test
  void loginWritesRefreshSecretOnlyToCookieAndOmitsItFromJson() {
    LoginRequest request = new LoginRequest();
    request.setEmail("jane@example.com"); request.setPassword("Password@123");
    AuthResponse issued = issued("raw-refresh-secret");
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    when(authService.login(eq(request), any())).thenReturn(issued);

    ResponseEntity<AuthResponse> response = controller.login(request, new MockHttpServletRequest(), servletResponse);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
    assertThat(response.getBody().getRefreshToken()).isNull();
    verify(refreshCookies).write(servletResponse, "raw-refresh-secret");
  }

  @Test
  void refreshReadsSecretFromCookieAndDoesNotAcceptItFromJson() {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    when(refreshCookies.read(servletRequest)).thenReturn(Optional.of("cookie-refresh-secret"));
    when(authService.refresh(any(RefreshRequest.class), any())).thenReturn(issued("rotated-secret"));

    ResponseEntity<AuthResponse> response = controller.refresh(servletRequest, servletResponse);

    ArgumentCaptor<RefreshRequest> requestCaptor = ArgumentCaptor.forClass(RefreshRequest.class);
    verify(authService).refresh(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue().getRefreshToken()).isEqualTo("cookie-refresh-secret");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getRefreshToken()).isNull();
    verify(refreshCookies).write(servletResponse, "rotated-secret");
  }

  private AuthResponse issued(String refreshToken) {
    return AuthResponse.builder().accessToken("access-token").refreshToken(refreshToken).tokenType("Bearer").expiresInSeconds(900L)
        .user(new UserResponse(UUID.randomUUID(), "Jane Doe", "jane@example.com", java.util.List.of("CUSTOMER"), UserStatus.ACTIVE, null, null)).build();
  }
}
