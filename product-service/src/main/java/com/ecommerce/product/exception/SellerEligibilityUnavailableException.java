package com.ecommerce.product.exception;

/** A failed eligibility check cannot authorize a catalogue write. */
public class SellerEligibilityUnavailableException extends RuntimeException {
  public SellerEligibilityUnavailableException() {
    super("Seller eligibility is temporarily unavailable. Please try again later.");
  }
}
