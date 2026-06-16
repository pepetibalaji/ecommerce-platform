package com.ecommerce.common.security.util;

import com.ecommerce.common.security.jwt.JwtClaimConstants;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtPrincipalUtils {

    private JwtPrincipalUtils() {}

    public static UUID getUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString(JwtClaimConstants.USER_ID);
        return UUID.fromString(userId);
    }

    public static String getEmail(Jwt jwt) {
        return jwt.getSubject();
    }

    public static String getRole(Jwt jwt) {
        return jwt.getClaimAsString(JwtClaimConstants.ROLE);
    }

    public static Integer getTokenVersion(Jwt jwt) {
        Object value = jwt.getClaims().get(JwtClaimConstants.TOKEN_VERSION);
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }
}