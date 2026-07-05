package com.ecommerce.payment;

import com.ecommerce.payment.config.PaymentProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
        "com.ecommerce.payment",
        "com.ecommerce.common"
})
@EnableConfigurationProperties(PaymentProviderProperties.class)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}