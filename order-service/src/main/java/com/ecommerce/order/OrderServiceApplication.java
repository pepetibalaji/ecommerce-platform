package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.ecommerce.order",
        "com.ecommerce.common.security",
        "com.ecommerce.common.exception",
        "com.ecommerce.common.proto",
        "com.ecommerce.common.grpc"
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}