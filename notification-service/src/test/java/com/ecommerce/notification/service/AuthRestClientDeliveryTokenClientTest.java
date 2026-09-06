package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.notification.config.AuthActionDeliveryProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthRestClientDeliveryTokenClientTest {
  @Test
  void sendsTheProtectedInternalRequestAndReadsOnlyTheReturnedToken() {
    AuthActionDeliveryProperties properties = new AuthActionDeliveryProperties();
    properties.setInternalServiceToken("test-internal-secret");
    RestClient.Builder builder = RestClient.builder().baseUrl("http://auth-service.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID actionId = UUID.randomUUID();
    server
        .expect(requestTo("http://auth-service.test/internal/auth/actions/" + actionId + "/delivery-token"))
        .andExpect(method(POST))
        .andExpect(header("X-Internal-Auth", "test-internal-secret"))
        .andRespond(withSuccess("{\"token\":\"delivery-token\"}", APPLICATION_JSON));

    String token =
        new AuthRestClientDeliveryTokenClient(builder.build(), properties).obtainDeliveryToken(actionId);

    assertThat(token).isEqualTo("delivery-token");
    server.verify();
  }
}
