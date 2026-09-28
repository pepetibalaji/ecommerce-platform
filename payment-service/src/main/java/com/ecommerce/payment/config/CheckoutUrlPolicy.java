package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
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
        // Stripe Checkout URLs legitimately contain a fragment used by Stripe's hosted page.
        // It is provider output, not a redirect target controlled by our frontend.
        URI uri = parse(value, true);
        boolean allowed = provider == PaymentProvider.STRIPE && "https".equals(uri.getScheme())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && properties.getCheckout().getAllowedProviderHosts().contains(uri.getHost().toLowerCase(Locale.ROOT));
        if (provider == PaymentProvider.SANDBOX && isDevelopment())
            allowed = "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()) && uri.getPort() == 3001;
        if (!allowed) {
            log.error("Checkout URL rejected: provider={}, scheme={}, host={}, port={}, allowedStripeHosts={}",
                    provider, uri.getScheme(), uri.getHost(), uri.getPort(),
                    properties.getCheckout().getAllowedProviderHosts());
            throw configurationError();
        }
    }

    public void validateReturnUrl(String value) {
        URI uri = parse(value, false);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        boolean secure = "https".equals(uri.getScheme())
                || (isDevelopment() && "http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1").contains(uri.getHost()));
        if (!secure || !"/payment/return".equals(uri.getPath())
                || !properties.getCheckout().getFrontendOrigins().contains(origin)) {
            log.error("Payment return URL rejected: scheme={}, host={}, port={}, path={}, configuredOrigins={}",
                    uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath(),
                    properties.getCheckout().getFrontendOrigins());
            throw configurationError();
        }
    }

    private URI parse(String value, boolean allowFragment) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || (!allowFragment && uri.getFragment() != null)) {
                log.error("Payment URL rejected during parsing: hostPresent={}, userInfoPresent={}, fragmentPresent={}",
                        uri.getHost() != null, uri.getUserInfo() != null, uri.getFragment() != null);
                throw configurationError();
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.error("Payment URL rejected during parsing: malformedOrMissing=true");
            throw configurationError();
        }
    }

    private PaymentApiException configurationError() {
        return new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_CONFIGURATION_ERROR);
    }
}
