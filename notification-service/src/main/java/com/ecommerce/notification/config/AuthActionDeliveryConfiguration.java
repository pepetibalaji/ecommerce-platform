package com.ecommerce.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthActionDeliveryConfiguration {
  @Bean
  RestClient authActionRestClient(
      RestClient.Builder builder, AuthActionDeliveryProperties properties) {
    return builder.baseUrl(properties.getAuthBaseUrl()).build();
  }
}
