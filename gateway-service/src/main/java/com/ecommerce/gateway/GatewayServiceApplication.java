package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import reactor.core.publisher.Hooks;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {

        Hooks.enableAutomaticContextPropagation();
        
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
