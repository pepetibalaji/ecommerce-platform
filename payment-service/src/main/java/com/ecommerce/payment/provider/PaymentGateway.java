package com.ecommerce.payment.provider;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.provider.model.RefundPaymentCommand;
import com.ecommerce.payment.provider.model.RefundPaymentResult;

public interface PaymentGateway {

    boolean supports(PaymentProvider provider);

    boolean isEnabled();

    CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command);

    void verifyWebhookSignature(String payload, String signature);

    ProviderWebhookEvent parseWebhookEvent(String payload, String signature);

    ProviderWebhookEvent getPaymentStatus(String providerPaymentId);

    RefundPaymentResult refundPayment(RefundPaymentCommand command);
}