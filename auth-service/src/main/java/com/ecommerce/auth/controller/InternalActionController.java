package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.DeliveryTokenResponse;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.service.ActionTokenService;
import com.ecommerce.auth.service.AuditRequestContext;
import com.ecommerce.auth.service.AuthAuditService;
import com.ecommerce.auth.security.InternalServiceAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/** Internal endpoint protected by the Notification-to-Auth service credential. */
@RestController @RequestMapping("/internal/auth/actions") @RequiredArgsConstructor
public class InternalActionController {
  private final ActionTokenService actions;
  private final InternalServiceAuthorizer authorizer;
  private final AuthAuditService audit;

  @PostMapping("/{actionId}/delivery-token")
  public DeliveryTokenResponse deliveryToken(
      @PathVariable UUID actionId,
      @RequestHeader(value = "X-Internal-Auth", required = false) String serviceToken,
      HttpServletRequest request) {
    try {
      authorizer.requireAuthorized(serviceToken);
    } catch (AccessDeniedException exception) {
      audit.recordAttempt(
          null,
          null,
          "INTERNAL_ACTION_DELIVERY_TOKEN_REQUEST",
          AuthAuditOutcome.DENIED,
          AuditRequestContext.from(request),
          java.util.Map.of("reason", "service_authentication_failed"));
      throw exception;
    }
    return actions.mintDeliveryToken(actionId, AuditRequestContext.from(request));
  }
}
