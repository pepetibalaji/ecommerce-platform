package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.UnauthorizedException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static final String EMAIL = "test@example.com";

  private static final String RAW_PASSWORD = "Password@123";

  private static final String ENCODED_PASSWORD = "encoded-password";

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private AuthenticationManager authenticationManager;

  @Mock private JwtTokenService jwtTokenService;

  @Mock private RefreshTokenService refreshTokenService;

  @Mock private TokenBlacklistService tokenBlacklistService;

  @InjectMocks private AuthService authService;

  private User user;

  @BeforeEach
  void setUp() {

    user =
        User.builder()
            .id(USER_ID)
            .name("Test User")
            .email(EMAIL)
            .password(ENCODED_PASSWORD)
            .role(Role.CUSTOMER)
            .status(UserStatus.ACTIVE)
            .tokenVersion(0L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
  }

  @Test
  void shouldRegisterUser() {

    RegisterRequest request = new RegisterRequest();

    request.setName(" Test User ");
    request.setEmail(" TEST@Example.COM ");
    request.setPassword(RAW_PASSWORD);

    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

    when(userRepository.save(any(User.class))).thenReturn(user);

    when(jwtTokenService.generateAccessToken(user)).thenReturn("access-token");

    when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

    AuthResponse result = authService.register(request);

    assertThat(result).isNotNull();

    assertThat(result.getAccessToken()).isEqualTo("access-token");

    assertThat(result.getRefreshToken()).isNotBlank();

    assertThat(result.getTokenType()).isEqualTo("Bearer");

    assertThat(result.getExpiresInSeconds()).isEqualTo(3600L);

    assertThat(result.getUser().getId()).isEqualTo(USER_ID);

    assertThat(result.getUser().getEmail()).isEqualTo(EMAIL);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

    verify(userRepository).save(userCaptor.capture());

    User savedUser = userCaptor.getValue();

    assertThat(savedUser.getName()).isEqualTo("Test User");

    assertThat(savedUser.getEmail()).isEqualTo(EMAIL);

    assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);

    assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

    assertThat(savedUser.getTokenVersion()).isEqualTo(0L);

    verify(refreshTokenService)
        .issue(eq(user), eq(result.getRefreshToken()), any(LocalDateTime.class));
  }

  @Test
  void shouldThrowWhenRegisterEmailAlreadyExists() {

    RegisterRequest request = new RegisterRequest();

    request.setName("Test User");
    request.setEmail(" TEST@Example.COM ");
    request.setPassword(RAW_PASSWORD);

    when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

    assertThrows(ResourceAlreadyExistsException.class, () -> authService.register(request));

    verify(userRepository, never()).save(any(User.class));

    verify(refreshTokenService, never()).issue(any(), anyString(), any(LocalDateTime.class));
  }

  @Test
  void shouldLoginUser() {

    LoginRequest request = new LoginRequest();

    request.setEmail(" TEST@Example.COM ");
    request.setPassword(RAW_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

    when(jwtTokenService.generateAccessToken(user)).thenReturn("access-token");

    when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

    AuthResponse result = authService.login(request);

    assertThat(result).isNotNull();

    assertThat(result.getAccessToken()).isEqualTo("access-token");

    assertThat(result.getRefreshToken()).isNotBlank();

    assertThat(result.getUser().getEmail()).isEqualTo(EMAIL);

    ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
        ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);

    verify(authenticationManager).authenticate(authCaptor.capture());

    UsernamePasswordAuthenticationToken authenticationToken = authCaptor.getValue();

    assertThat(authenticationToken.getPrincipal()).isEqualTo(EMAIL);

    assertThat(authenticationToken.getCredentials()).isEqualTo(RAW_PASSWORD);

    verify(refreshTokenService)
        .issue(eq(user), eq(result.getRefreshToken()), any(LocalDateTime.class));
  }

  @Test
  void shouldThrowWhenLoginCredentialsAreInvalid() {

    LoginRequest request = new LoginRequest();

    request.setEmail(EMAIL);
    request.setPassword("wrong-password");

    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    assertThrows(UnauthorizedException.class, () -> authService.login(request));

    verify(userRepository, never()).findByEmail(anyString());

    verify(refreshTokenService, never()).issue(any(), anyString(), any(LocalDateTime.class));
  }

  @Test
  void shouldThrowWhenLoginUserNotFoundAfterAuthentication() {

    LoginRequest request = new LoginRequest();

    request.setEmail(EMAIL);
    request.setPassword(RAW_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThrows(UnauthorizedException.class, () -> authService.login(request));

    verify(jwtTokenService, never()).generateAccessToken(any(User.class));
  }

  @Test
  void shouldThrowWhenLoginUserInactive() {

    user.setStatus(UserStatus.INACTIVE);

    LoginRequest request = new LoginRequest();

    request.setEmail(EMAIL);
    request.setPassword(RAW_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

    assertThrows(UnauthorizedException.class, () -> authService.login(request));

    verify(jwtTokenService, never()).generateAccessToken(any(User.class));

    verify(refreshTokenService, never()).issue(any(), anyString(), any(LocalDateTime.class));
  }

  @Test
  void shouldRefreshToken() {

    RefreshRequest request = new RefreshRequest();

    request.setRefreshToken("old-refresh-token");

    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("old-refresh-token")
            .expiry(LocalDateTime.now().plusDays(7))
            .build();

    when(refreshTokenService.findByToken("old-refresh-token"))
        .thenReturn(Optional.of(refreshToken));

    when(jwtTokenService.generateAccessToken(user)).thenReturn("new-access-token");

    when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

    AuthResponse result = authService.refresh(request);

    assertThat(result).isNotNull();

    assertThat(result.getAccessToken()).isEqualTo("new-access-token");

    assertThat(result.getRefreshToken()).isNotBlank();

    assertThat(result.getRefreshToken()).isNotEqualTo("old-refresh-token");

    verify(refreshTokenService).revoke("old-refresh-token");

    verify(refreshTokenService)
        .issue(eq(user), eq(result.getRefreshToken()), any(LocalDateTime.class));
  }

  @Test
  void shouldThrowWhenRefreshTokenNotFound() {

    RefreshRequest request = new RefreshRequest();

    request.setRefreshToken("missing-refresh-token");

    when(refreshTokenService.findByToken("missing-refresh-token")).thenReturn(Optional.empty());

    assertThrows(UnauthorizedException.class, () -> authService.refresh(request));

    verify(refreshTokenService, never()).revoke(anyString());

    verify(jwtTokenService, never()).generateAccessToken(any(User.class));
  }

  @Test
  void shouldThrowAndRevokeWhenRefreshTokenExpired() {

    RefreshRequest request = new RefreshRequest();

    request.setRefreshToken("expired-refresh-token");

    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("expired-refresh-token")
            .expiry(LocalDateTime.now().minusMinutes(1))
            .build();

    when(refreshTokenService.findByToken("expired-refresh-token"))
        .thenReturn(Optional.of(refreshToken));

    assertThrows(UnauthorizedException.class, () -> authService.refresh(request));

    verify(refreshTokenService).revoke("expired-refresh-token");

    verify(jwtTokenService, never()).generateAccessToken(any(User.class));
  }

  @Test
  void shouldThrowWhenRefreshUserInactive() {

    user.setStatus(UserStatus.INACTIVE);

    RefreshRequest request = new RefreshRequest();

    request.setRefreshToken("refresh-token");

    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("refresh-token")
            .expiry(LocalDateTime.now().plusDays(7))
            .build();

    when(refreshTokenService.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

    assertThrows(UnauthorizedException.class, () -> authService.refresh(request));

    verify(refreshTokenService, never()).revoke("refresh-token");

    verify(jwtTokenService, never()).generateAccessToken(any(User.class));
  }

  @Test
  void shouldLogoutCurrentSessionWithRefreshToken() {

    Jwt jwt = jwt(USER_ID.toString());

    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("refresh-token")
            .expiry(LocalDateTime.now().plusDays(7))
            .build();

    when(refreshTokenService.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

    authService.logout(jwt, "refresh-token");

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService).revoke("refresh-token");

    verify(refreshTokenService, never()).revokeAllForUser(USER_ID);
  }

  @Test
  void shouldLogoutAllSessionsWhenRefreshTokenIsMissing() {

    Jwt jwt = jwt(USER_ID.toString());

    authService.logout(jwt, null);

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService).revokeAllForUser(USER_ID);
  }

  @Test
  void shouldLogoutAllSessionsWhenRefreshTokenIsBlank() {

    Jwt jwt = jwt(USER_ID.toString());

    authService.logout(jwt, "   ");

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService).revokeAllForUser(USER_ID);
  }

  @Test
  void shouldLogoutAllSessionsWhenRefreshTokenNotFound() {

    Jwt jwt = jwt(USER_ID.toString());

    when(refreshTokenService.findByToken("missing-refresh-token")).thenReturn(Optional.empty());

    authService.logout(jwt, "missing-refresh-token");

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService).revokeAllForUser(USER_ID);
  }

  @Test
  void shouldThrowWhenLogoutRefreshTokenBelongsToDifferentUser() {

    Jwt jwt = jwt(USER_ID.toString());

    User otherUser =
        User.builder()
            .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .name("Other User")
            .email("other@example.com")
            .role(Role.CUSTOMER)
            .status(UserStatus.ACTIVE)
            .tokenVersion(0L)
            .build();

    RefreshToken refreshToken =
        RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(otherUser)
            .token("refresh-token")
            .expiry(LocalDateTime.now().plusDays(7))
            .build();

    when(refreshTokenService.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

    assertThrows(UnauthorizedException.class, () -> authService.logout(jwt, "refresh-token"));

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService, never()).revoke("refresh-token");

    verify(refreshTokenService, never()).revokeAllForUser(USER_ID);
  }

  @Test
  void shouldThrowWhenLogoutAccessTokenMissing() {

    assertThrows(UnauthorizedException.class, () -> authService.logout(null, "refresh-token"));

    verify(tokenBlacklistService, never()).blacklistToken(any(Jwt.class));
  }

  @Test
  void shouldThrowWhenLogoutUserIdClaimMissing() {

    Jwt jwt = jwtWithoutUserId();

    assertThrows(UnauthorizedException.class, () -> authService.logout(jwt, "refresh-token"));

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService, never()).revoke(anyString());
  }

  @Test
  void shouldThrowWhenLogoutUserIdClaimInvalid() {

    Jwt jwt = jwt("not-a-uuid");

    assertThrows(UnauthorizedException.class, () -> authService.logout(jwt, "refresh-token"));

    verify(tokenBlacklistService).blacklistToken(jwt);

    verify(refreshTokenService, never()).revoke(anyString());
  }

  @Test
  void shouldSupportMultipleLoginsForSameUser() {

    LoginRequest request = new LoginRequest();

    request.setEmail(EMAIL);
    request.setPassword(RAW_PASSWORD);

    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

    when(jwtTokenService.generateAccessToken(user))
        .thenReturn("access-token-device-1")
        .thenReturn("access-token-device-2");

    when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

    AuthResponse firstLogin = authService.login(request);

    AuthResponse secondLogin = authService.login(request);

    assertThat(firstLogin.getRefreshToken()).isNotBlank();

    assertThat(secondLogin.getRefreshToken()).isNotBlank();

    assertThat(firstLogin.getRefreshToken()).isNotEqualTo(secondLogin.getRefreshToken());

    verify(refreshTokenService, never()).revokeAllForUser(USER_ID);
  }

  private Jwt jwt(String userIdClaim) {

    Instant now = Instant.now();

    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer("http://localhost:8081")
        .subject(EMAIL)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600))
        .jti("jwt-id")
        .claim("userId", userIdClaim)
        .claim("role", "CUSTOMER")
        .claim("status", "ACTIVE")
        .claim("tokenVersion", 0L)
        .build();
  }

  private Jwt jwtWithoutUserId() {

    Instant now = Instant.now();

    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer("http://localhost:8081")
        .subject(EMAIL)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600))
        .jti("jwt-id")
        .claim("role", "CUSTOMER")
        .claim("status", "ACTIVE")
        .claim("tokenVersion", 0L)
        .build();
  }
}
