package com.ecommerce.payment.provider;

import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentGatewayFactory {
    private final PaymentProviderProperties properties;
    private final List<PaymentGateway> gateways;
    public PaymentGateway getActiveGateway() { return getGateway(properties.getProvider().getActive()); }
    public PaymentGateway getGateway(PaymentProvider provider) {
        if (provider == null || provider == PaymentProvider.RAZORPAY) {
            log.error("Payment gateway configuration is invalid: requestedProvider={}", provider);
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
        }
        return gateways.stream().filter(candidate -> candidate.supports(provider) && candidate.isEnabled())
                .findFirst().orElseThrow(() -> {
                    log.error("Payment gateway configuration is invalid: requestedProvider={}, activeProvider={}, stripeEnabled={}, sandboxEnabled={}",
                            provider, properties.getProvider().getActive(), properties.getProvider().getStripe().isEnabled(),
                            properties.getProvider().getSandbox().isEnabled());
                    return new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
                });
    }
}
