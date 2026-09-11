package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.product.exception.SellerEligibilityUnavailableException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SellerEligibilityClientTest {
  private static final UUID SELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final String BASE_URL = "http://auth.test";
  private MockRestServiceServer server;
  private RestClient restClient;
  private SellerEligibilityClient eligibility;

  @BeforeEach
  void setup() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    restClient = builder.build();
    eligibility = new SellerEligibilityClient(restClient, "test-service-secret");
  }

  @Test
  void validEligibilitySendsUuidAndServiceCredential() {
    server.expect(requestTo(BASE_URL + "/internal/auth/sellers/" + SELLER_ID + "/eligibility"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-Internal-Auth", "test-service-secret"))
        .andRespond(withSuccess("{\"eligible\":true}", MediaType.APPLICATION_JSON));
    assertThatCode(() -> eligibility.requireEligible(SELLER_ID)).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void definitiveIneligibilityIsABadRequest() {
    server.expect(requestTo(BASE_URL + "/internal/auth/sellers/" + SELLER_ID + "/eligibility"))
        .andRespond(withSuccess("{\"eligible\":false}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> eligibility.requireEligible(SELLER_ID)).isInstanceOf(BadRequestException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "null", "{\"eligible\":null}", "not-json",
      "{\"eligible\":\"true\"}", "{\"eligible\":1}", "true", "[]"})
  void missingOrMalformedEligibilityFailsClosedAsUnavailable(String body) {
    server.expect(requestTo(BASE_URL + "/internal/auth/sellers/" + SELLER_ID + "/eligibility"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> eligibility.requireEligible(SELLER_ID))
        .isInstanceOf(SellerEligibilityUnavailableException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 302, 307, 401, 403, 404, 429, 500, 503})
  void downstreamFailureNeverAuthorizesSellerOrLeaksResponse(int status) {
    server.expect(requestTo(BASE_URL + "/internal/auth/sellers/" + SELLER_ID + "/eligibility"))
        .andRespond(withStatus(HttpStatus.valueOf(status)).body("private-downstream-error"));
    assertThatThrownBy(() -> eligibility.requireEligible(SELLER_ID))
        .isInstanceOf(SellerEligibilityUnavailableException.class)
        .hasMessageNotContaining("private-downstream-error")
        .hasMessageNotContaining("test-service-secret");
    server.verify();
  }

  @Test
  void timeoutFailsClosed() {
    server.expect(requestTo(BASE_URL + "/internal/auth/sellers/" + SELLER_ID + "/eligibility"))
        .andRespond(withException(new SocketTimeoutException("test read timeout")));
    assertThatThrownBy(() -> eligibility.requireEligible(SELLER_ID))
        .isInstanceOf(SellerEligibilityUnavailableException.class);
    server.verify();
  }

  @Test
  void absentSellerOrCredentialMakesNoHttpCall() {
    assertThatThrownBy(() -> eligibility.requireEligible(null)).isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> new SellerEligibilityClient(restClient, "").requireEligible(SELLER_ID))
        .isInstanceOf(SellerEligibilityUnavailableException.class);
    assertThatThrownBy(() -> new SellerEligibilityClient(restClient, "${AUTH_INTERNAL_SERVICE_TOKEN:}")
        .requireEligible(SELLER_ID)).isInstanceOf(SellerEligibilityUnavailableException.class);
    server.verify();
  }

  @Test
  void deployedConfigurationRequiresResolvedCredentialAndPositiveTimeouts() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    assertThatThrownBy(() -> new SellerEligibilityClient(BASE_URL, "", Duration.ofSeconds(2),
        Duration.ofSeconds(3), environment)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new SellerEligibilityClient(BASE_URL, "test-service-secret", Duration.ZERO,
        Duration.ofSeconds(3), environment)).isInstanceOf(IllegalArgumentException.class);
    environment.setProperty("AUTH_INTERNAL_SERVICE_TOKEN", "runtime-test-secret");
    assertThatCode(() -> new SellerEligibilityClient(BASE_URL, "${AUTH_INTERNAL_SERVICE_TOKEN:}",
        Duration.ofSeconds(2), Duration.ofSeconds(3), environment)).doesNotThrowAnyException();
  }

  @Test
  void defaultTimeoutsAndCredentialBindWhenSpringCreatesTheClient() {
    new ApplicationContextRunner()
        .withPropertyValues("auth.internal.base-url=http://auth.test", "auth.internal.service-token=test-service-secret")
        .withBean(SellerEligibilityClient.class)
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SellerEligibilityClient.class));
  }
}
