package com.ecommerce.auth.security;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findAuthorizationDataByEmailNormalized(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

    List<String> authorities = new ArrayList<>();
    user.getRoleCodes().forEach(role -> authorities.add("ROLE_" + role));
    user.getPermissionCodes().forEach(permission -> authorities.add("PERMISSION_" + permission));

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPasswordHash())
        .authorities(authorities.toArray(String[]::new))
        .accountExpired(false)
        .accountLocked(!user.isActiveAndVerified())
        .credentialsExpired(false)
        .disabled(!user.isActiveAndVerified())
        .build();
  }
}
