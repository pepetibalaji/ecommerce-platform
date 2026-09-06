package com.ecommerce.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/** Request details suitable for an Auth audit row. Do not add request bodies or credentials here. */
public record AuditRequestContext(String ipAddress, String userAgent) {

  private static final int MAX_USER_AGENT_LENGTH = 512;

  public static AuditRequestContext empty() {
    return new AuditRequestContext(null, null);
  }

  public static AuditRequestContext from(HttpServletRequest request) {
    if (request == null) {
      return empty();
    }

    String userAgent = request.getHeader("User-Agent");
    if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
      userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
    // Only use the direct peer address. Trusting X-Forwarded-For must be configured at the gateway
    // or platform level, otherwise callers can forge it.
    return new AuditRequestContext(request.getRemoteAddr(), userAgent);
  }
}
