package com.ecommerce.notification.service;

import com.ecommerce.notification.config.AuthActionDeliveryProperties;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * The Auth token is requested only when an email is about to be sent. It is never accepted from,
 * logged from, or persisted from a Kafka event.
 */
@Component
public class AuthRestClientDeliveryTokenClient implements AuthDeliveryTokenClient {
  private final RestClient client;
  private final AuthActionDeliveryProperties properties;

  public AuthRestClientDeliveryTokenClient(
      RestClient authActionRestClient, AuthActionDeliveryProperties properties) {
    this.client = authActionRestClient;
    this.properties = properties;
  }

  @Override
  public String obtainDeliveryToken(UUID actionId) {
    properties.requireInternalServiceToken();
    DeliveryTokenResponse response =
        client
            .post()
            .uri("/internal/auth/actions/{actionId}/delivery-token", actionId)
            .header("X-Internal-Auth", properties.getInternalServiceToken())
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, clientResponse) -> {
                  throw new IllegalStateException(
                      "Auth rejected identity action delivery-token request: "
                          + clientResponse.getStatusCode().value());
                })
            .body(DeliveryTokenResponse.class);
    if (response == null || !StringUtils.hasText(response.token())) {
      throw new IllegalStateException("Auth returned no identity action delivery token");
    }
    return response.token();
  }

  private record DeliveryTokenResponse(String token) {}
}
