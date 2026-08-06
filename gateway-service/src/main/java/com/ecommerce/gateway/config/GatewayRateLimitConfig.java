package com.ecommerce.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfig {

    private final boolean trustForwardedFor;

    public GatewayRateLimitConfig(
            @Value("${gateway.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.trustForwardedFor = trustForwardedFor;
    }

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .cast(Principal.class)
                .map(principal -> resolvePrincipalKey(principal))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + resolveClientIp(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + resolveClientIp(exchange));
    }

    private String resolvePrincipalKey(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String userId = jwtAuthenticationToken.getToken().getClaimAsString("userId");
            if (StringUtils.hasText(userId)) {
                return "user:" + userId;
            }

            String subject = jwtAuthenticationToken.getToken().getSubject();
            if (StringUtils.hasText(subject)) {
                return "sub:" + subject;
            }
        }

        if (principal instanceof AbstractAuthenticationToken authenticationToken
                && StringUtils.hasText(authenticationToken.getName())) {
            return "principal:" + authenticationToken.getName();
        }

        return principal.getName();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        if (trustForwardedFor) {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }

            String realIp = request.getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }

        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse("unknown");
    }
}
