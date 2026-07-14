package com.ecommerce.payment.provider.razorpay;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.provider.model.RefundGatewayRequest;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import com.ecommerce.payment.provider.model.RefundPaymentCommand;
import com.ecommerce.payment.provider.model.RefundPaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGateway {

    private final PaymentProviderProperties properties;

    @Override
    public boolean supports(PaymentProvider provider) {
        return PaymentProvider.RAZORPAY == provider;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProvider().getRazorpay().isEnabled();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        throw new BadRequestException("Razorpay integration is adapter-ready but not implemented in PAYMENT-102");
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        throw new BadRequestException("Razorpay webhook verification is not implemented in PAYMENT-102");
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String payload, String signature) {
        throw new BadRequestException("Razorpay webhook parsing is not implemented in PAYMENT-102");
    }

    @Override
    public ProviderWebhookEvent getPaymentStatus(String providerPaymentId) {
        throw new BadRequestException("Razorpay payment status lookup is not implemented in PAYMENT-102");
    }

    @Override
    public RefundPaymentResult refundPayment(RefundPaymentCommand command) {
        throw new BadRequestException("Razorpay refund is not implemented in PAYMENT-102");
    }

    @Override
    public RefundGatewayResponse refund(RefundGatewayRequest request) {
        return new RefundGatewayResponse(
                false,
                null,
                "UNSUPPORTED",
                "Razorpay refund is not implemented yet"
        );
    }
}