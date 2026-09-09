package com.ecommerce.gateway.config;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import reactor.core.publisher.Mono;

/**
 * Gateway's JWT decoder must remain non-blocking because Spring Cloud Gateway
 * runs on WebFlux. It also applies the same token-version revocation check as
 * the downstream servlet resource services.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayJwtDecoderConfig {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    public ReactiveJwtDecoder gatewayReactiveJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            ReactiveStringRedisTemplate redis
    ) {
        NimbusReactiveJwtDecoder delegate = NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();
        delegate.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

        return token -> delegate.decode(token)
                .flatMap(jwt -> validateTokenVersion(jwt, redis));
    }

    private Mono<Jwt> validateTokenVersion(Jwt jwt, ReactiveStringRedisTemplate redis) {
        String userId = jwt.getClaimAsString("userId");
        Object tokenVersion = jwt.getClaim("tokenVersion");
        if (userId == null || tokenVersion == null) {
            return Mono.error(invalidatedToken());
        }

        return redis.opsForValue().get("auth:token-version:" + userId)
                .filter(current -> Objects.equals(current, String.valueOf(tokenVersion)))
                .map(ignored -> jwt)
                .switchIfEmpty(Mono.error(invalidatedToken()));
    }

    private JwtValidationException invalidatedToken() {
        return new JwtValidationException(
                "Token has been invalidated",
                List.of(new OAuth2Error("invalid_token", "Token has been invalidated", null))
        );
    }
}
