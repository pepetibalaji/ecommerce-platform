package com.ecommerce.notification.service;

import java.util.UUID;

/** Obtains a one-time action token from Auth over the service-to-service boundary. */
public interface AuthDeliveryTokenClient {
  String obtainDeliveryToken(UUID actionId);
}
