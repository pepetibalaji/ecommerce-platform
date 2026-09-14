package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CheckoutUrlPolicy {
    private final PaymentProviderProperties properties;
    private final Environment environment;

    public boolean isDevelopment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length > 0 && Arrays.stream(profiles)
                .allMatch(profile -> Set.of("dev", "local", "test").contains(profile));
    }

    public void validateCheckoutUrl(PaymentProvider provider, String value) {
        URI uri = parse(value);
        boolean allowed = provider == PaymentProvider.STRIPE && "https".equals(uri.getScheme())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && properties.getCheckout().getAllowedProviderHosts().contains(uri.getHost().toLowerCase(Locale.ROOT));
        if (provider == PaymentProvider.SANDBOX && isDevelopment())
            allowed = "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()) && uri.getPort() == 3001;
        if (!allowed) throw configurationError();
    }

    public void validateReturnUrl(String value) {
        URI uri = parse(value);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        boolean secure = "https".equals(uri.getScheme())
                || (isDevelopment() && "http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1").contains(uri.getHost()));
        if (!secure || !"/payment/return".equals(uri.getPath())
                || !properties.getCheckout().getFrontendOrigins().contains(origin))
            throw configurationError();
    }

    private URI parse(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw configurationError();
            return uri;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw configurationError();
        }
    }

    private PaymentApiException configurationError() {
        return new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
    }
}
