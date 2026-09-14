package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProviderSafety implements InitializingBean {
    private final PaymentProviderProperties properties;
    private final CheckoutUrlPolicy urls;

    @Override
    public void afterPropertiesSet() {
        var provider = properties.getProvider();
        if (provider.getRazorpay().isEnabled() || provider.getActive() == PaymentProvider.RAZORPAY)
            throw new IllegalStateException("Razorpay must remain disabled until its full integration is verified");
        if (!urls.isDevelopment()) {
            if (provider.getSandbox().isEnabled() || provider.getActive() == PaymentProvider.SANDBOX)
                throw new IllegalStateException("Sandbox is only permitted in explicit dev/local/test profiles");
            if (provider.getStripe().isEnabled() && !provider.getStripe().isStagingVerified())
                throw new IllegalStateException("Stripe requires staging verification before enabling outside development");
        }
        validateTemplate(properties.getCheckout().getSuccessUrl());
        validateTemplate(properties.getCheckout().getCancelUrl());
    }

    private void validateTemplate(String value) {
        if (!value.contains("orderId={ORDER_ID}") || !value.contains("paymentId={PAYMENT_ID}"))
            throw new IllegalStateException("Frontend payment return URLs must include orderId and paymentId");
        urls.validateReturnUrl(value.replace("{ORDER_ID}", "00000000-0000-0000-0000-000000000000")
                .replace("{PAYMENT_ID}", "00000000-0000-0000-0000-000000000000"));
    }
}
