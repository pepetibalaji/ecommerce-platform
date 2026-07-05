package com.ecommerce.payment.provider;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentGatewayFactory {

    private final PaymentProviderProperties properties;

    private final List<PaymentGateway> gateways;

    public PaymentGateway getActiveGateway() {
        return getGateway(properties.getProvider().getActive());
    }

    public PaymentGateway getGateway(PaymentProvider provider) {
        if (provider == null) {
            throw new BadRequestException("Payment provider is required");
        }

        PaymentGateway gateway = gateways.stream()
                .filter(candidate -> candidate.supports(provider))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Payment provider is not supported: " + provider
                ));

        if (!gateway.isEnabled()) {
            throw new BadRequestException(
                    "Payment provider is disabled: " + provider
            );
        }

        return gateway;
    }
}