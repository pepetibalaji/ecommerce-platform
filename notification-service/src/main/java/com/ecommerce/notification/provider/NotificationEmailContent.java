package com.ecommerce.notification.provider;

import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Creates minimal transactional email content without logging security-sensitive action links. */
@Component
public class NotificationEmailContent {
  private final ObjectMapper mapper;

  public NotificationEmailContent(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String subject(Notification notification) {
    return switch (notification.getType()) {
      case "EMAIL_VERIFICATION" -> "Verify your email address";
      case "PASSWORD_RESET" -> "Reset your password";
      case "EMAIL_CHANGE_CONFIRMATION" -> "Confirm your new email address";
      case "ORDER_RECEIVED" -> "We received your order";
      case "PAYMENT_SUCCESSFUL" -> "Your payment was successful";
      case "PAYMENT_FAILED" -> "Your payment failed";
      case "ORDER_CANCELLED" -> "Your order was cancelled";
      case "REFUND_PROCESSED" -> "Your refund was processed";
      case "ORDER_SHIPPED" -> "Your order has shipped";
      case "ORDER_DELIVERED" -> "Your order was delivered";
      case "LOW_STOCK_WARNING" -> "Low stock warning";
      case "SELLER_NEW_ORDER" -> "You have a new paid order";
      default -> "Ecommerce Platform notification";
    };
  }

  public String body(Notification notification) throws Exception {
    JsonNode payload = mapper.readTree(notification.getPayload());
    String actionUrl = payload.path("actionUrl").asText("");
    if (!actionUrl.isBlank()) {
      return switch (notification.getType()) {
        case "EMAIL_VERIFICATION" ->
            "Verify your email address by opening this link:\n\n" + actionUrl;
        case "PASSWORD_RESET" -> "Reset your password by opening this link:\n\n" + actionUrl;
        case "EMAIL_CHANGE_CONFIRMATION" ->
            "Confirm your new email address by opening this link:\n\n" + actionUrl;
        default -> subject(notification);
      };
    }
    String orderId = payload.path("orderId").asText("");
    return orderId.isBlank() ? subject(notification) : subject(notification) + ". Order: " + orderId;
  }
}
