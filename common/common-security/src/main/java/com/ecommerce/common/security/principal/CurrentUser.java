package com.ecommerce.common.security.principal;

import java.util.UUID;

public record CurrentUser(
    UUID userId,
    String email,
    String role,
    Integer tokenVersion
) {}