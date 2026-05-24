package com.ecommerce.auth;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = "com.ecommerce"
)
public class AuthServiceApplication {
	static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
