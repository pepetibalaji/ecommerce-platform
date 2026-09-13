package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.ecommerce.inventory",
        "com.ecommerce.common.security",
        "com.ecommerce.common.exception"
})
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                InventoryServiceApplication.class,
                args
        );
    }
}
