package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.SellerEligibilityResponse;
import com.ecommerce.auth.security.InternalServiceAuthorizer;
import com.ecommerce.auth.service.AccountService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal eligibility contract; responses deliberately contain no account details. */
@RestController
@RequestMapping("/internal/auth/sellers")
@RequiredArgsConstructor
public class InternalSellerController {
  private final AccountService accounts;
  private final InternalServiceAuthorizer authorizer;

  @GetMapping("/{sellerId}/eligibility")
  public SellerEligibilityResponse eligibility(
      @PathVariable UUID sellerId,
      @RequestHeader(value = "X-Internal-Auth", required = false) String token) {
    authorizer.requireAuthorized(token);
    return new SellerEligibilityResponse(accounts.isEligibleSeller(sellerId));
  }
}
