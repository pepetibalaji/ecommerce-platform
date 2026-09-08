package com.ecommerce.common.security.config;

import com.ecommerce.common.security.jwt.JwtAuthoritiesConverter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.ecommerce.common.security.jwt.TokenVersionValidator;

@Configuration
public class JwtResourceServerSupportConfig {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                                 StringRedisTemplate redis) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), new TokenVersionValidator(redis)));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthoritiesConverter jwtAuthoritiesConverter() {
        return new JwtAuthoritiesConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtAuthoritiesConverter jwtAuthoritiesConverter
    ) {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                jwtAuthoritiesConverter
        );

        return converter;
    }
}
