package com.ecommerce.auth;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ecommerce.auth", "com.ecommerce.common.exception"})
@EnableScheduling
public class AuthServiceApplication {

  static {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
