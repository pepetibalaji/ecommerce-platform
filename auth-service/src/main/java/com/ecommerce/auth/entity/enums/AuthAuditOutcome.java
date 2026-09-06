package com.ecommerce.auth.entity.enums;

/** Outcome of a security-relevant action retained in the immutable Auth audit trail. */
public enum AuthAuditOutcome {
  SUCCESS,
  FAILURE,
  DENIED
}
