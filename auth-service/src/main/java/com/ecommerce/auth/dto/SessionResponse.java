package com.ecommerce.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** Safe self-service representation of a refresh session; it never contains a refresh token. */
public record SessionResponse(
    UUID id,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    String deviceName,
    String ipAddress,
    String userAgent) {}
