package com.ecommerce.payment.provider.stripe;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.dto.response.ProviderRefundStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;

import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.provider.model.RefundGatewayRequest;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import com.ecommerce.payment.provider.model.RefundPaymentCommand;
import com.ecommerce.payment.provider.model.RefundPaymentResult;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final PaymentProviderProperties properties;

    @Override
    public boolean supports(PaymentProvider provider) {
        return PaymentProvider.STRIPE == provider;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProvider().getStripe().isEnabled();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        validateStripeConfig();

        long amountInMinorUnit = toMinorUnit(command.getAmount());

        String successUrl = command.getSuccessUrl() == null ? replacePlaceholders(properties.getCheckout().getSuccessUrl(), command) : command.getSuccessUrl();
        String cancelUrl = command.getCancelUrl() == null ? replacePlaceholders(properties.getCheckout().getCancelUrl(), command) : command.getCancelUrl();

        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Order " + command.getOrderId())
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(command.getCurrency().toLowerCase(Locale.ROOT))
                        .setUnitAmount(amountInMinorUnit)
                        .setProductData(productData)
                        .build();

        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .setClientReferenceId(command.getPaymentId().toString())
                        .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("paymentId", command.getPaymentId().toString())
                                .putMetadata("attemptIdempotencyKey", command.getIdempotencyKey()).build())
                        .setExpiresAt(command.getExpiresAt() == null ? null : command.getExpiresAt().getEpochSecond())
                        .addLineItem(lineItem)
                        .putMetadata("paymentId", command.getPaymentId().toString())
                        .putMetadata("orderId", command.getOrderId().toString())
                        .putMetadata("userId", command.getUserId().toString())
                        .putMetadata("attemptIdempotencyKey", command.getIdempotencyKey())
                        .build();

        RequestOptions requestOptions =
                RequestOptions.builder()
                        .setApiKey(properties.getProvider().getStripe().getApiKey())
                        .setIdempotencyKey(command.getIdempotencyKey())
                        .setConnectTimeout(timeoutMs()).setReadTimeout(timeoutMs()).setMaxNetworkRetries(0)
                        .build();

        try {
            Session session = Session.create(params, requestOptions);

            return CheckoutSessionResult.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerSessionId(session.getId())
                    .providerPaymentIntentId(session.getPaymentIntent())
                    .checkoutUrl(session.getUrl())
                    .expiresAt(toInstant(session.getExpiresAt()))
                    .build();
        } catch (StripeException exception) {
            throw new com.ecommerce.payment.exception.PaymentApiException(
                    com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, exception);
        }
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        validateStripeConfig();

        try {
            verifiedEvent(payload, signature);
        } catch (SignatureVerificationException exception) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid Stripe webhook payload");
        }
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String payload, String signature) {
        validateStripeConfig();

        Event event;
        try {
            event = verifiedEvent(payload, signature);
        } catch (SignatureVerificationException exception) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid Stripe webhook payload");
        }

        String eventType = event.getType();
        Optional<StripeObject> objectOptional = event.getDataObjectDeserializer().getObject();

        if (objectOptional.isEmpty()) {
            if (eventType.startsWith("checkout.session.")) {
                // The envelope signature is verified. Read only the stable fields of the signed
                // checkout object; an SDK version mismatch must not turn a browser/status lookup
                // into payment proof or require a network call before retaining the verified event.
                com.fasterxml.jackson.databind.JsonNode raw;
                try { raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.getDataObjectDeserializer().getRawJson()); }
                catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new BadRequestException("Invalid Stripe checkout event"); }
                if (!raw.path("id").isTextual() || !raw.path("id").asText().startsWith("cs_")
                        || !"checkout.session".equals(raw.path("object").asText()))
                    throw new BadRequestException("Invalid Stripe checkout event");
                ProviderPaymentStatus signedStatus = switch (eventType) {
                    case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                            "paid".equals(raw.path("payment_status").asText()) ? ProviderPaymentStatus.SUCCESS : ProviderPaymentStatus.PROCESSING;
                    case "checkout.session.async_payment_failed" -> ProviderPaymentStatus.FAILED;
                    case "checkout.session.expired" -> ProviderPaymentStatus.EXPIRED;
                    default -> ProviderPaymentStatus.IGNORED;
                };
                String paymentId = raw.path("metadata").path("paymentId").asText(null);
                java.util.UUID metadataId = null;
                if (paymentId != null) {
                    try { metadataId = java.util.UUID.fromString(paymentId); }
                    catch (IllegalArgumentException invalid) { throw new BadRequestException("Invalid Stripe payment metadata"); }
                }
                return ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId(event.getId())
                        .eventType(eventType).status(signedStatus).providerSessionId(raw.path("id").asText())
                        .providerPaymentIntentId(raw.path("payment_intent").isTextual() ? raw.path("payment_intent").asText() : null)
                        .paymentId(metadataId).attemptIdempotencyKey(raw.path("metadata").path("attemptIdempotencyKey").asText(null))
                        .amount(raw.path("amount_total").isNumber() ? raw.path("amount_total").decimalValue().movePointLeft(2) : null)
                        .currency(raw.path("currency").asText(null)).build();
            }
            return ignored(event.getId(), eventType, "Unsupported Stripe event object");
        }

        StripeObject stripeObject = objectOptional.get();

        if (("checkout.session.completed".equals(eventType) || "checkout.session.async_payment_succeeded".equals(eventType))
                && stripeObject instanceof Session session) {
            ProviderPaymentStatus status = "paid".equalsIgnoreCase(session.getPaymentStatus())
                    ? ProviderPaymentStatus.SUCCESS
                    : ProviderPaymentStatus.PROCESSING;

            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .paymentId(paymentId(session)).attemptIdempotencyKey(attemptKey(session))
                    .amount(session.getAmountTotal() == null ? null : BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2)).currency(session.getCurrency())
                    .status(status)
                    .providerSessionId(session.getId())
                    .providerPaymentIntentId(session.getPaymentIntent())
                    .build();
        }

        if ("checkout.session.async_payment_failed".equals(eventType) && stripeObject instanceof Session session) {
            return ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId(event.getId())
                    .eventType(eventType).paymentId(paymentId(session)).attemptIdempotencyKey(attemptKey(session))
                    .amount(session.getAmountTotal() == null ? null : BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2)).currency(session.getCurrency()).providerSessionId(session.getId()).providerPaymentIntentId(session.getPaymentIntent())
                    .status(ProviderPaymentStatus.FAILED).failureReason("Stripe delayed payment failed").build();
        }

        if ("checkout.session.expired".equals(eventType) && stripeObject instanceof Session session) {
            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .status(ProviderPaymentStatus.EXPIRED)
                    .paymentId(paymentId(session)).attemptIdempotencyKey(attemptKey(session))
                    .amount(session.getAmountTotal() == null ? null : BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2)).currency(session.getCurrency())
                    .providerSessionId(session.getId())
                    .providerPaymentIntentId(session.getPaymentIntent())
                    .failureReason("Stripe checkout session expired")
                    .build();
        }

        if ("payment_intent.payment_failed".equals(eventType) && stripeObject instanceof PaymentIntent paymentIntent) {
            String failureReason = paymentIntent.getLastPaymentError() != null
                    ? paymentIntent.getLastPaymentError().getMessage()
                    : "Stripe payment intent failed";

            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .status(ProviderPaymentStatus.FAILED)
                    .providerPaymentIntentId(paymentIntent.getId())
                    .paymentId(metadataPaymentId(paymentIntent.getMetadata()))
                    .attemptIdempotencyKey(paymentIntent.getMetadata() == null ? null : paymentIntent.getMetadata().get("attemptIdempotencyKey"))
                    .amount(paymentIntent.getAmount() == null ? null : BigDecimal.valueOf(paymentIntent.getAmount()).movePointLeft(2))
                    .currency(paymentIntent.getCurrency())
                    .failureReason(failureReason)
                    .build();
        }

        if (isRefundEvent(eventType) && stripeObject instanceof Refund refund) {
            ProviderRefundStatus refundStatus = mapRefundStatus(refund.getStatus());

            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .status(ProviderPaymentStatus.IGNORED)
                    .refundStatus(refundStatus)
                    .providerRefundId(refund.getId())
                    .providerPaymentIntentId(refund.getPaymentIntent())
                    .providerChargeId(refund.getCharge())
                    .refundAmount(refund.getAmount() == null
                            ? null
                            : BigDecimal.valueOf(refund.getAmount()).movePointLeft(2))
                    .failureReason(refund.getFailureReason())
                    .build();
        }

        return ignored(event.getId(), eventType, "Stripe event type ignored by payment-service");
    }

    @Override
    public ProviderWebhookEvent getPaymentStatus(String providerPaymentId) {
        validateStripeApiKeyConfig();
        if (providerPaymentId == null || !providerPaymentId.startsWith("cs_"))
            throw new BadRequestException("A saved Stripe checkout session is required");
        try {
            int timeout = (int) Math.min(5000, properties.getProvider().getStripe().getTimeoutMs());
            Session session = Session.retrieve(providerPaymentId, RequestOptions.builder()
                    .setApiKey(properties.getProvider().getStripe().getApiKey())
                    .setConnectTimeout(timeout).setReadTimeout(timeout).setMaxNetworkRetries(0).build());
            ProviderPaymentStatus status = "paid".equals(session.getPaymentStatus()) ? ProviderPaymentStatus.SUCCESS
                    : "expired".equals(session.getStatus()) ? ProviderPaymentStatus.EXPIRED
                    : "complete".equals(session.getStatus()) ? ProviderPaymentStatus.PROCESSING : ProviderPaymentStatus.IGNORED;
            return ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE)
                    .providerEventId("lookup:" + session.getId() + ":" + status).eventType("stripe.checkout.reconciled")
                    .paymentId(paymentId(session)).attemptIdempotencyKey(attemptKey(session))
                    .amount(session.getAmountTotal() == null ? null : BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2)).currency(session.getCurrency())
                    .providerSessionId(session.getId()).providerPaymentIntentId(session.getPaymentIntent()).status(status).build();
        } catch (StripeException exception) {
            throw new com.ecommerce.payment.exception.PaymentConfirmationUnavailableException();
        }
    }

    @Override
    public RefundPaymentResult refundPayment(RefundPaymentCommand command) {
        return RefundPaymentResult.builder()
                .successful(false)
                .failureReason("Refund is implemented in PAYMENT-103")
                .build();
    }

    @Override
    public RefundGatewayResponse refund(RefundGatewayRequest request) {
        validateStripeApiKeyConfig();

        try {
            long amountInMinorUnit = toMinorUnit(request.amount());

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(request.providerPaymentIntentId())
                    .setAmount(amountInMinorUnit)
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(properties.getProvider().getStripe().getApiKey())
                    .setIdempotencyKey(request.idempotencyKey())
                    .setConnectTimeout(timeoutMs()).setReadTimeout(timeoutMs()).setMaxNetworkRetries(0)
                    .build();

            Refund refund = Refund.create(params, requestOptions);

            return new RefundGatewayResponse(
                    true,
                    refund.getId(),
                    refund.getStatus(),
                    null
            );
        } catch (StripeException exception) {
            // A timeout may follow provider acceptance. Preserve uncertainty and replay the durable key.
            throw new com.ecommerce.payment.exception.PaymentApiException(
                    com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private boolean isRefundEvent(String eventType) {
        return "refund.created".equals(eventType)
                || "refund.updated".equals(eventType)
                || "charge.refunded".equals(eventType)
                || "charge.refund.updated".equals(eventType);
    }

    private ProviderRefundStatus mapRefundStatus(String stripeStatus) {
        if (stripeStatus == null || stripeStatus.isBlank()) {
            return ProviderRefundStatus.PROCESSING;
        }

        String normalized = stripeStatus.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "pending" -> ProviderRefundStatus.PROCESSING;
            case "succeeded", "success", "refunded" -> ProviderRefundStatus.SUCCESS;
            case "failed", "canceled", "cancelled" -> ProviderRefundStatus.FAILED;
            default -> ProviderRefundStatus.PROCESSING;
        };
    }

    private ProviderWebhookEvent ignored(String eventId, String eventType, String reason) {
        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.STRIPE)
                .providerEventId(eventId)
                .eventType(eventType)
                .status(ProviderPaymentStatus.IGNORED)
                .failureReason(reason)
                .build();
    }

    private void validateStripeConfig() {
        if (!properties.getProvider().getStripe().isEnabled()) {
            throw new com.ecommerce.payment.exception.PaymentApiException(com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }

        if (properties.getProvider().getStripe().getApiKey() == null
                || properties.getProvider().getStripe().getApiKey().isBlank()) {
            throw new com.ecommerce.payment.exception.PaymentApiException(com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }

        if (properties.getProvider().getStripe().getWebhookSecret() == null
                || properties.getProvider().getStripe().getWebhookSecret().isBlank()) {
            throw new com.ecommerce.payment.exception.PaymentApiException(com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }
    }

    private void validateStripeApiKeyConfig() {
        if (!properties.getProvider().getStripe().isEnabled()) {
            throw new com.ecommerce.payment.exception.PaymentApiException(com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }

        if (properties.getProvider().getStripe().getApiKey() == null
                || properties.getProvider().getStripe().getApiKey().isBlank()) {
            throw new com.ecommerce.payment.exception.PaymentApiException(com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }
    }

    private long toMinorUnit(BigDecimal amount) {
        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String replacePlaceholders(String url, CreateCheckoutSessionCommand command) {
        return url
                .replace("{ORDER_ID}", command.getOrderId().toString())
                .replace("{PAYMENT_ID}", command.getPaymentId().toString());
    }

    private Instant toInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private int timeoutMs() {
        return (int) Math.min(10000, properties.getProvider().getStripe().getTimeoutMs());
    }

    private java.util.UUID paymentId(Session session) {
        return metadataPaymentId(session.getMetadata());
    }

    private java.util.UUID metadataPaymentId(java.util.Map<String, String> metadata) {
        String value = metadata == null ? null : metadata.get("paymentId");
        if (value == null) return null;
        try { return java.util.UUID.fromString(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private String attemptKey(Session session) {
        return session.getMetadata() == null ? null : session.getMetadata().get("attemptIdempotencyKey");
    }

    @Override
    public RefundGatewayResponse retrieveRefund(String providerRefundId) {
        validateStripeApiKeyConfig();
        try {
            Refund refund = Refund.retrieve(providerRefundId, RequestOptions.builder()
                    .setApiKey(properties.getProvider().getStripe().getApiKey())
                    .setConnectTimeout(timeoutMs()).setReadTimeout(timeoutMs()).setMaxNetworkRetries(0).build());
            return new RefundGatewayResponse(true, refund.getId(), refund.getStatus(), refund.getFailureReason());
        } catch (StripeException exception) {
            throw new com.ecommerce.payment.exception.PaymentApiException(
                    com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private Event verifiedEvent(String payload, String signature) throws SignatureVerificationException {
        java.util.List<String> secrets = new java.util.ArrayList<>();
        secrets.add(properties.getProvider().getStripe().getWebhookSecret());
        secrets.addAll(properties.getProvider().getStripe().getPreviousWebhookSecrets());
        SignatureVerificationException last = null;
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) continue;
            try { return Webhook.constructEvent(payload, signature, secret); }
            catch (SignatureVerificationException invalid) { last = invalid; }
        }
        if (last != null) throw last;
        throw new com.ecommerce.payment.exception.PaymentApiException(
                com.ecommerce.payment.exception.PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
    }
}
