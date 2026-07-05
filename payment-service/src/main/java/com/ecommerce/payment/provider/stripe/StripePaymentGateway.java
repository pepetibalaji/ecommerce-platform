package com.ecommerce.payment.provider.stripe;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.provider.model.RefundPaymentCommand;
import com.ecommerce.payment.provider.model.RefundPaymentResult;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

        String successUrl = replaceOrderId(properties.getCheckout().getSuccessUrl(), command);
        String cancelUrl = replaceOrderId(properties.getCheckout().getCancelUrl(), command);

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
                        .addLineItem(lineItem)
                        .putMetadata("paymentId", command.getPaymentId().toString())
                        .putMetadata("orderId", command.getOrderId().toString())
                        .putMetadata("userId", command.getUserId().toString())
                        .build();

        RequestOptions requestOptions =
                RequestOptions.builder()
                        .setApiKey(properties.getProvider().getStripe().getApiKey())
                        .setIdempotencyKey(command.getIdempotencyKey())
                        .build();

        try {
            Session session = Session.create(params, requestOptions);

            return CheckoutSessionResult.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerSessionId(session.getId())
                    .providerPaymentIntentId(session.getPaymentIntent())
                    .checkoutUrl(session.getUrl())
                    .expiresAt(toLocalDateTime(session.getExpiresAt()))
                    .build();

        } catch (StripeException exception) {
            throw new BadRequestException(
                    "Failed to create Stripe checkout session: " + exception.getMessage()
            );
        }
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        validateStripeConfig();

        try {
            Webhook.constructEvent(
                    payload,
                    signature,
                    properties.getProvider().getStripe().getWebhookSecret()
            );
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
            event = Webhook.constructEvent(
                    payload,
                    signature,
                    properties.getProvider().getStripe().getWebhookSecret()
            );
        } catch (SignatureVerificationException exception) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid Stripe webhook payload");
        }

        String eventType = event.getType();
        Optional<StripeObject> objectOptional = event.getDataObjectDeserializer().getObject();

        if (objectOptional.isEmpty()) {
            return ignored(event.getId(), eventType, "Stripe event object could not be deserialized");
        }

        StripeObject stripeObject = objectOptional.get();

        if ("checkout.session.completed".equals(eventType) && stripeObject instanceof Session session) {
            ProviderPaymentStatus status = "paid".equalsIgnoreCase(session.getPaymentStatus())
                    ? ProviderPaymentStatus.SUCCESS
                    : ProviderPaymentStatus.PROCESSING;

            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .status(status)
                    .providerSessionId(session.getId())
                    .providerPaymentIntentId(session.getPaymentIntent())
                    .build();
        }

        if ("checkout.session.expired".equals(eventType) && stripeObject instanceof Session session) {
            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .providerEventId(event.getId())
                    .eventType(eventType)
                    .status(ProviderPaymentStatus.CANCELLED)
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
                    .failureReason(failureReason)
                    .build();
        }

        return ignored(event.getId(), eventType, "Stripe event type ignored by payment-service");
    }

    @Override
    public ProviderWebhookEvent getPaymentStatus(String providerPaymentId) {
        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.STRIPE)
                .eventType("stripe.status.lookup.not-implemented")
                .status(ProviderPaymentStatus.IGNORED)
                .providerPaymentIntentId(providerPaymentId)
                .failureReason("Provider status lookup is not implemented in PAYMENT-102")
                .build();
    }

    @Override
    public RefundPaymentResult refundPayment(RefundPaymentCommand command) {
        return RefundPaymentResult.builder()
                .successful(false)
                .failureReason("Refund is implemented in PAYMENT-103")
                .build();
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
            throw new BadRequestException("Stripe provider is disabled");
        }

        if (properties.getProvider().getStripe().getApiKey() == null
                || properties.getProvider().getStripe().getApiKey().isBlank()) {
            throw new BadRequestException("Stripe API key is not configured");
        }

        if (properties.getProvider().getStripe().getWebhookSecret() == null
                || properties.getProvider().getStripe().getWebhookSecret().isBlank()) {
            throw new BadRequestException("Stripe webhook secret is not configured");
        }
    }

    private long toMinorUnit(BigDecimal amount) {
        return amount
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String replaceOrderId(String url, CreateCheckoutSessionCommand command) {
        return url
                .replace("{ORDER_ID}", command.getOrderId().toString())
                .replace("{PAYMENT_ID}", command.getPaymentId().toString());
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return LocalDateTime.now().plusMinutes(30);
        }

        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.UTC
        );
    }
}