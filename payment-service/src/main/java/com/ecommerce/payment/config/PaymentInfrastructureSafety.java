package com.ecommerce.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentInfrastructureSafety {
    private final Environment environment;
    @PostConstruct void validate() {
        var profiles=Arrays.asList(environment.getActiveProfiles());
        boolean development=!profiles.isEmpty() && profiles.stream().allMatch(Set.of("dev","local","test")::contains);
        if(development) return;
        if(!"SASL_SSL".equals(environment.getProperty("spring.kafka.properties.security.protocol")))
            throw new IllegalStateException("Payment production Kafka requires SASL_SSL and authorized service credentials");
        if(environment.getProperty("payment.order-lookup.secret","").trim().length()<32)
            throw new IllegalStateException("Payment trusted Order lookup requires a shared secret of at least 32 characters");
    }
}
