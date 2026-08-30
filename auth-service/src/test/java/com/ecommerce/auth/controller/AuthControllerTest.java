package com.ecommerce.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LogoutRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer; // CRITICAL: Import the micrometer Tracer
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AuthController authController;

  @MockBean private AuthService authService;

  @MockBean private JwtDecoder jwtDecoder;

  // CRITICAL FIX: Mocks the telemetry tracer infrastructure for clean slice-testing execution
  @MockBean private Tracer tracer;

  @Test
  void shouldRegisterUser() throws Exception {

    RegisterRequest request = new RegisterRequest();

    request.setName("Test User");
    request.setEmail("test@example.com");
    request.setPassword("Password@123");

    AuthResponse response = authResponse("access-token", "refresh-token");

    when(authService.register(any(RegisterRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresInSeconds").value(3600))
        .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.user.email").value("test@example.com"))
        .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.user.status").value("ACTIVE"));
  }

  @Test
  void shouldLoginUser() throws Exception {

    LoginRequest request = new LoginRequest();

    request.setEmail("test@example.com");
    request.setPassword("Password@123");

    AuthResponse response = authResponse("access-token", "refresh-token");

    when(authService.login(any(LoginRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.user.email").value("test@example.com"));
  }

  @Test
  void shouldRefreshToken() throws Exception {

    RefreshRequest request = new RefreshRequest();

    request.setRefreshToken("old-refresh-token");

    AuthResponse response = authResponse("new-access-token", "new-refresh-token");

    when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void shouldLogoutCurrentSession() {

    Jwt jwt = jwt();

    LogoutRequest request = new LogoutRequest();

    request.setRefreshToken("refresh-token");

    authController.logout(jwt, request);

    verify(authService).logout(jwt, "refresh-token");
  }

  @Test
  void shouldLogoutWithoutRefreshToken() {

    Jwt jwt = jwt();

    authController.logout(jwt, null);

    verify(authService).logout(jwt, null);
  }

  @Test
  void shouldRejectInvalidRegisterRequest() throws Exception {

    RegisterRequest request = new RegisterRequest();

    request.setName("");
    request.setEmail("invalid-email");
    request.setPassword("");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectInvalidLoginRequest() throws Exception {

    LoginRequest request = new LoginRequest();

    request.setEmail("");
    request.setPassword("");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  private AuthResponse authResponse(String accessToken, String refreshToken) {
    UserResponse user =
        new UserResponse(
            USER_ID,
            "Test User",
            "test@example.com",
            Role.CUSTOMER,
            UserStatus.ACTIVE,
            LocalDateTime.now(),
            LocalDateTime.now());

    return new AuthResponse(accessToken, refreshToken, "Bearer", 3600L, user);
  }

  private Jwt jwt() {

    Instant now = Instant.now();

    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer("http://localhost:8081")
        .subject("test@example.com")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600))
        .jti("jwt-id")
        .claim("userId", USER_ID.toString())
        .claim("role", "CUSTOMER")
        .claim("status", "ACTIVE")
        .claim("tokenVersion", 0L)
        .build();
  }
}
