package com.ecommerce.common.security.jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object roleClaim = jwt.getClaims().get(JwtClaimConstants.ROLE);

        if (roleClaim == null) {
            return Collections.emptyList();
        }

        if (roleClaim instanceof String role) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        }

        if (roleClaim instanceof Collection<?> roles) {
            return roles.stream()
                    .map(String::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}