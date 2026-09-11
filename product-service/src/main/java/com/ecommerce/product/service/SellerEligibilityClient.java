package com.ecommerce.product.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.product.exception.SellerEligibilityUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SellerEligibilityClient {
  private final RestClient client;
  private final String token;

  @Autowired
  public SellerEligibilityClient(
      @Value("${auth.internal.base-url:http://localhost:8081}") String baseUrl,
      @Value("${auth.internal.service-token:}") String configuredToken,
      @Value("${auth.internal.connect-timeout:PT2S}") String connectTimeout,
      @Value("${auth.internal.read-timeout:PT3S}") String readTimeout,
      Environment environment) {
    this(baseUrl, configuredToken, Duration.parse(connectTimeout), Duration.parse(readTimeout), environment);
  }

  SellerEligibilityClient(String baseUrl, String configuredToken, Duration connectTimeout, Duration readTimeout,
      Environment environment) {
    if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
        || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
      throw new IllegalArgumentException("Auth eligibility timeouts must be positive");
    }
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
        .connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
    requestFactory.setReadTimeout(readTimeout);
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    String runtimeToken = environment.getProperty("AUTH_INTERNAL_SERVICE_TOKEN");
    this.token = runtimeToken != null && !runtimeToken.isBlank() ? runtimeToken : configuredToken;
    if (environment.matchesProfiles("stage", "prod") && !hasUsableToken()) {
      throw new IllegalStateException("Auth eligibility service credential must be configured in stage/prod");
    }
  }

  SellerEligibilityClient(RestClient client, String token) {
    this.client = client;
    this.token = token;
  }

  public void requireEligible(UUID sellerId) {
    if (sellerId == null) {
      throw new BadRequestException("sellerId is required");
    }
    if (!hasUsableToken()) {
      throw new SellerEligibilityUnavailableException();
    }
    JsonNode response;
    try {
      response = client.get().uri("/internal/auth/sellers/{id}/eligibility", sellerId)
          .header("X-Internal-Auth", token).retrieve()
          .onStatus(status -> !status.is2xxSuccessful(), (request, downstream) -> {
            throw new SellerEligibilityUnavailableException();
          })
          .body(JsonNode.class);
    } catch (RestClientException exception) {
      // Do not expose downstream response bodies, URLs, or credentials to browser clients.
      throw new SellerEligibilityUnavailableException();
    }
    if (response == null || !response.isObject() || !response.path("eligible").isBoolean()) {
      throw new SellerEligibilityUnavailableException();
    }
    if (!response.path("eligible").booleanValue()) {
      throw new BadRequestException("Seller is not eligible to own products");
    }
  }

  private boolean hasUsableToken() {
    return token != null && !token.isBlank() && !token.contains("${");
  }
}
