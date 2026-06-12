package com.ecommerce.auth.config;

import com.ecommerce.auth.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

@Configuration
public class TokenCustomizerConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(UserRepository userRepository) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String email = context.getPrincipal().getName();

            userRepository.findByEmail(email).ifPresent(user -> {
                context.getClaims().subject(user.getEmail());
                context.getClaims().claim("userId", user.getId().toString());
                context.getClaims().claim("role", user.getRole().name());
                context.getClaims().claim("status", user.getStatus().name());
            });
        };
    }
}