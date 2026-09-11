package com.ecommerce.auth;

import com.ecommerce.auth.config.BrowserSessionProperties;
import java.util.TimeZone;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ecommerce.auth", "com.ecommerce.common.exception"})
@EnableScheduling
@EnableConfigurationProperties(BrowserSessionProperties.class)
public class AuthServiceApplication {

  static {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
