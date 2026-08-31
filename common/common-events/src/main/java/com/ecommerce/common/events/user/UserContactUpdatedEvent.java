package com.ecommerce.common.events.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal recipient-directory event. It deliberately excludes credentials, tokens, and profile data
 * other than the email address required for transactional delivery.
 */
public record UserContactUpdatedEvent(
    UUID eventId, UUID userId, String email, boolean active, Instant occurredAt) {}
