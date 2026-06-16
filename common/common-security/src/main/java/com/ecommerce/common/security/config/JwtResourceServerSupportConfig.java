package com.ecommerce.common.security.config;

import com.ecommerce.common.security.jwt.JwtAuthoritiesConverter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class JwtResourceServerSupportConfig {

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