package com.ecommerce.notification.provider;

import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/** Renders event-specific Pepekart HTML for SMTP providers, including Mailpit. */
@Component
public class SmtpEmailContent {
  private static final String VERIFICATION_TEMPLATE = "email/pepekart-email-verification.html";
  private final ObjectMapper mapper;
  private final NotificationEmailContent text;
  private final String verificationHtml;

  public SmtpEmailContent(ObjectMapper mapper, NotificationEmailContent text) throws IOException {
    this.mapper = mapper;
    this.text = text;
    this.verificationHtml = new ClassPathResource(VERIFICATION_TEMPLATE)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  public String html(Notification notification) throws Exception {
    JsonNode payload = mapper.readTree(notification.getPayload());
    String type = notification.getType();
    if ("EMAIL_VERIFICATION".equals(type)) {
      return verificationHtml.replace("{{{verificationUrl}}}",
          escape(requiredActionUrl(payload, type)));
    }

    String heading = text.subject(notification);
    String message = switch (type) {
      case "PASSWORD_RESET" -> "We received a request to reset your password. Use the button below to choose a new one.";
      case "EMAIL_CHANGE_CONFIRMATION" -> "Please confirm your new email address to finish updating your Pepekart account.";
      case "ORDER_RECEIVED" -> "Your payment is complete and your order is confirmed. Thanks for shopping with Pepekart.";
      case "PAYMENT_SUCCESSFUL" -> "Your payment went through. Your order is moving forward.";
      case "PAYMENT_FAILED" -> "We could not complete your payment. Please check your payment method and try again.";
      case "ORDER_CANCELLED" -> "Your order has been cancelled. You can find the order details below.";
      case "REFUND_PROCESSED" -> "Your refund has been processed. Your payment provider may need time to show it.";
      case "ORDER_SHIPPED" -> "Good news: your order is on its way.";
      case "ORDER_DELIVERED" -> "Your order has been delivered. We hope you enjoy it.";
      case "LOW_STOCK_WARNING" -> "A product is running low. Review its stock level when you can.";
      case "OUT_OF_STOCK_WARNING" -> "A product is out of stock and needs attention.";
      case "SELLER_NEW_ORDER" -> "A new paid order is ready for you to review.";
      default -> "There is an update to your Pepekart account.";
    };
    String eyebrow = switch (type) {
      case "PASSWORD_RESET", "EMAIL_CHANGE_CONFIRMATION" -> "ACCOUNT SECURITY";
      case "LOW_STOCK_WARNING", "OUT_OF_STOCK_WARNING", "SELLER_NEW_ORDER" -> "SELLER UPDATE";
      default -> "ORDER UPDATE";
    };
    String accent = switch (type) {
      case "PAYMENT_FAILED", "LOW_STOCK_WARNING", "OUT_OF_STOCK_WARNING" -> "#b45309";
      case "ORDER_CANCELLED" -> "#b42318";
      default -> "#14532d";
    };
    String action = switch (type) {
      case "PASSWORD_RESET" -> actionButton(payload, type, "Reset password");
      case "EMAIL_CHANGE_CONFIRMATION" -> actionButton(payload, type, "Confirm new email");
      default -> "";
    };
    String accountNote = switch (type) {
      case "PASSWORD_RESET", "EMAIL_CHANGE_CONFIRMATION" ->
          "If you did not request this, you can safely ignore this email.";
      default -> "This is an update from Pepekart about your account or order.";
    };

    return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>" + escape(heading) + " | Pepekart</title></head>"
        + "<body style=\"margin:0;padding:0;background:#f3f5f2;color:#17251c;"
        + "font-family:Arial,Helvetica,sans-serif\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
        + " style=\"background:#f3f5f2;padding:32px 12px\"><tr><td align=\"center\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
        + " style=\"max-width:600px;background:#ffffff;border:1px solid #e2e8df;"
        + "border-radius:16px;overflow:hidden\">"
        + "<tr><td style=\"background:#123c2a;padding:24px 36px;color:#ffffff;"
        + "font-size:26px;font-weight:700;letter-spacing:-1px\">Pepekart"
        + "<span style=\"color:#d9f49a\">.</span></td></tr>"
        + "<tr><td style=\"padding:36px\">"
        + "<p style=\"margin:0 0 12px;color:" + accent + ";font-size:12px;"
        + "font-weight:700;letter-spacing:2px\">" + eyebrow + "</p>"
        + "<h1 style=\"margin:0 0 16px;color:#17251c;font-size:28px;"
        + "line-height:1.25\">" + escape(heading) + "</h1>"
        + "<p style=\"margin:0 0 24px;color:#47564b;font-size:16px;line-height:1.6\">"
        + escape(message) + "</p>" + details(payload) + action
        + "<p style=\"margin:28px 0 0;color:#657269;font-size:13px;line-height:1.6\">"
        + escape(accountNote) + "</p></td></tr>"
        + "<tr><td style=\"padding:20px 36px;background:#edf3ec;color:#657269;"
        + "font-size:12px;line-height:1.5\">Pepekart &nbsp;&middot;&nbsp;"
        + "Thank you for being with us.</td></tr></table></td></tr></table></body></html>";
  }

  private String details(JsonNode payload) {
    StringBuilder rows = new StringBuilder();
    for (List<String> field : List.of(
        List.of("orderId", "Order"), List.of("paymentId", "Payment"),
        List.of("refundId", "Refund"), List.of("amount", "Amount"),
        List.of("currency", "Currency"), List.of("productId", "Product"),
        List.of("availableStock", "Available stock"))) {
      String value = payload.path(field.get(0)).asText();
      if (StringUtils.hasText(value)) {
        rows.append("<tr><td style=\"padding:10px 16px;color:#657269;")
            .append("font-size:13px;border-bottom:1px solid #e2e8df\">")
            .append(field.get(1)).append("</td><td style=\"padding:10px 16px;")
            .append("color:#17251c;font-size:13px;font-weight:700;")
            .append("border-bottom:1px solid #e2e8df;overflow-wrap:anywhere\">")
            .append(escape(value)).append("</td></tr>");
      }
    }
    if (rows.isEmpty()) return "";
    return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\""
        + " cellspacing=\"0\" style=\"margin:0 0 24px;background:#f7f9f5;"
        + "border:1px solid #e2e8df;border-radius:10px\">"
        + rows + "</table>";
  }

  private String actionButton(JsonNode payload, String type, String label) {
    String url = escape(requiredActionUrl(payload, type));
    return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\">"
        + "<tr><td style=\"background:#d9f49a;border-radius:8px\">"
        + "<a href=\"" + url + "\" style=\"display:inline-block;padding:15px 24px;"
        + "color:#123c2a;text-decoration:none;font-size:15px;font-weight:700\">"
        + label + "</a></td></tr></table>"
        + "<p style=\"margin:20px 0 0;color:#657269;font-size:12px;line-height:1.6\">"
        + "If the button does not work, open this link:<br><a href=\"" + url
        + "\" style=\"color:#14532d;overflow-wrap:anywhere\">" + url + "</a></p>";
  }

  private String requiredActionUrl(JsonNode payload, String type) {
    String url = payload.path("actionUrl").asText();
    if (!StringUtils.hasText(url)) {
      throw new IllegalStateException("Action URL is required for " + type);
    }
    return url;
  }

  private String escape(String value) {
    return HtmlUtils.htmlEscape(value);
  }
}
