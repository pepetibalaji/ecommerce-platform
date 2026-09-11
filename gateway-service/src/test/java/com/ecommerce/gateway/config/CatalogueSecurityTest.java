package com.ecommerce.gateway.config;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Production gateway security chain with a stub catalogue handler, not a live Product backend. */
@WebFluxTest(controllers = CatalogueSecurityTest.StubCatalogueController.class, properties = {
    "spring.cloud.config.enabled=false",
    "gateway.cors.allowed-origins=http://localhost:5173"
})
@Import({SecurityConfig.class, CatalogueSecurityTest.StubCatalogueController.class})
class CatalogueSecurityTest {
  @Autowired private WebTestClient client;
  @MockitoBean private ReactiveJwtDecoder jwtDecoder;
  @MockitoBean private Tracer tracer;

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/v1/products", "/api/v1/products/", "/api/v1/products/facets",
      "/api/v1/products/20000000-0000-0000-0000-000000000001"
  })
  void anonymousCatalogueReadsReachHandler(String path) {
    client.get().uri(path).exchange()
        .expectStatus().isOk()
        .expectBody().json("{\"handled\":true}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/products", "/api/v1/products/20000000-0000-0000-0000-000000000001"})
  void anonymousCatalogueWritesAreUnauthorized(String path) {
    client.post().uri(path).exchange().expectStatus().isUnauthorized();
    client.put().uri(path).exchange().expectStatus().isUnauthorized();
    client.delete().uri(path).exchange().expectStatus().isUnauthorized();
  }

  @Test
  void sellerAdminAndInternalPathsAreNotAnonymousCatalogueEndpoints() {
    client.get().uri("/api/v1/seller/products").exchange().expectStatus().isUnauthorized();
    client.get().uri("/api/v1/admin/products").exchange().expectStatus().isUnauthorized();
    client.get().uri("/internal/auth/sellers/20000000-0000-0000-0000-000000000001/eligibility")
        .exchange().expectStatus().isUnauthorized();
  }

  @Test
  void internalAuthContractIsDeniedAtPublicGatewayEvenForAdmin() {
    client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .get().uri("/internal/auth/sellers/20000000-0000-0000-0000-000000000001/eligibility")
        .header("X-Internal-Auth", "internal-seller-contract-test-secret")
        .exchange().expectStatus().isForbidden();
  }

  @ParameterizedTest
  @ValueSource(strings = {"stripe", "razorpay"})
  void onlyProviderWebhookPostsBypassUserAuthentication(String provider) {
    String path = "/api/v1/payments/webhooks/" + provider;
    client.post().uri(path).exchange().expectStatus().isOk();
    client.get().uri(path).exchange().expectStatus().isUnauthorized();
    client.delete().uri(path).exchange().expectStatus().isUnauthorized();
    client.post().uri("/api/v1/payments/orders/20000000-0000-0000-0000-000000000001/refresh")
        .exchange().expectStatus().isUnauthorized();
    client.post().uri("/api/v1/payments/webhooks/unknown").exchange().expectStatus().isUnauthorized();
  }

  @RestController
  static class StubCatalogueController {
    @RequestMapping({"/api/v1/products", "/api/v1/products/**", "/api/v1/payments/webhooks/stripe", "/api/v1/payments/webhooks/razorpay"})
    Map<String, Boolean> catalogue() {
      return Map.of("handled", true);
    }
  }
}
