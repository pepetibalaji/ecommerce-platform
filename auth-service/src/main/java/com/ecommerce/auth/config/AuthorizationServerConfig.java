package com.ecommerce.auth.config;

import com.ecommerce.auth.security.JwtBlacklistValidator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            ResponseTraceFilter responseTraceFilter) throws Exception { // Injected here
        
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer ->
                        configurer.oidc(Customizer.withDefaults()))
                // Binds trace header injection to incoming protocol parsing
                .addFilterBefore(responseTraceFilter, SecurityContextHolderFilter.class) 
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        authorizationServerConfigurer.getEndpointsMatcher()
                ));

        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8081")
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            AuthorizationServerSettings authorizationServerSettings,
            JwtBlacklistValidator jwtBlacklistValidator
    ) {
        JwtDecoder jwtDecoder = org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration
                .OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);

        if (jwtDecoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
            OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> validator =
                    new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(authorizationServerSettings.getIssuer()),
                            jwtBlacklistValidator
                    );
            nimbusJwtDecoder.setJwtValidator(validator);
        }

        return jwtDecoder;
    }
}
