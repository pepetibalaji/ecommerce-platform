package com.ecommerce.auth.service;

import com.ecommerce.common.exception.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** Fixed-window controls keyed by both client IP and a non-reversible normalized-email hash. */
@Service
@RequiredArgsConstructor
public class AuthAbuseProtection {
  private final RedisTemplate<String, String> redis;

  public void check(String action, String email, AuditRequestContext context) {
    checkKey("ip", action, context == null || context.ipAddress() == null ? "unknown" : context.ipAddress(), 30);
    if (email != null && !email.isBlank()) checkKey("email", action, hash(email.trim().toLowerCase()), 10);
  }

  private void checkKey(String subject, String action, String value, int limit) {
    String key = "auth:rate:" + subject + ":" + action + ":" + value;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) redis.expire(key, Duration.ofMinutes(1));
    if (count == null || count > limit) throw new TooManyRequestsException();
  }

  private String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception exception) { throw new IllegalStateException("Unable to hash rate-limit key", exception); }
  }
}
