package com.ecommerce.auth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.auth.config.SecurityConfig;
import com.ecommerce.auth.security.InternalServiceAuthorizer;
import com.ecommerce.auth.service.AccountService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the production servlet security chains and real shared-secret verifier. */
@WebMvcTest(controllers = InternalSellerController.class, properties = {
    "spring.cloud.config.enabled=false",
    "AUTH_INTERNAL_SERVICE_TOKEN=internal-seller-contract-test-secret"
})
@Import({SecurityConfig.class, InternalServiceAuthorizer.class})
class InternalSellerSecurityTest {
  private static final UUID SELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final String PATH = "/internal/auth/sellers/" + SELLER_ID + "/eligibility";
  private static final String SECRET = "internal-seller-contract-test-secret";

  @Autowired private MockMvc mvc;
  @MockitoBean private AccountService accounts;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void validServiceCredentialReachesEligibilityWithoutBrowserJwt() throws Exception {
    when(accounts.isEligibleSeller(SELLER_ID)).thenReturn(true);
    mvc.perform(get(PATH).header("X-Internal-Auth", SECRET))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"eligible\":true}", true));
  }

  @Test
  void ineligibleSellerReturnsOnlyTheBooleanContract() throws Exception {
    when(accounts.isEligibleSeller(SELLER_ID)).thenReturn(false);
    mvc.perform(get(PATH).header("X-Internal-Auth", SECRET))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"eligible\":false}", true));
  }

  @Test
  void absentAndWrongCredentialsAreRejectedBeforeAccountLookup() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isForbidden());
    mvc.perform(get(PATH).header("X-Internal-Auth", "wrong-secret"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(accounts);
  }

  @Test
  void browserAdminJwtCannotReplaceServiceCredential() throws Exception {
    mvc.perform(get(PATH).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(accounts);
  }

  @Test
  void unspecifiedInternalPathsAndMethodsAreDenied() throws Exception {
    mvc.perform(get("/internal/auth/users").header("X-Internal-Auth", SECRET))
        .andExpect(status().isForbidden());
    mvc.perform(post(PATH).header("X-Internal-Auth", SECRET))
        .andExpect(status().isForbidden());
    verifyNoInteractions(accounts);
  }
}
