package com.ecommerce.auth.config;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
@RequiredArgsConstructor
public class TokenCustomizerConfig {

    private final UserRepository userRepository;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String email = context.getPrincipal().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new OAuth2Error("invalid_grant", "User not found: " + email, null)
                    ));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_grant", "User account is not active", null)
                );
            }

            context.getClaims()
                    .subject(user.getEmail())
                    .claim("userId", user.getId().toString())
                    .claim("role", user.getRole().name())
                    .claim("status", user.getStatus().name())
                    .claim("tokenVersion", user.getTokenVersion() == null ? 0L : user.getTokenVersion());
        };
    }
}