package com.ecommerce.notification.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Dedicated SMTP connection for the configurable SMTP email provider. */
@Configuration
@ConditionalOnProperty(name = "notification.provider", havingValue = "smtp")
public class SmtpEmailConfiguration {
  @Bean("notificationSmtpSender")
  public JavaMailSender notificationSmtpSender(NotificationProperties properties) {
    var config = properties.getSmtp();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(config.getHost());
    sender.setPort(config.getPort());
    sender.setUsername(config.getUsername());
    sender.setPassword(config.getPassword());
    Properties mailProperties = sender.getJavaMailProperties();
    mailProperties.put("mail.smtp.auth", Boolean.toString(config.isAuth()));
    mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(config.isStarttls()));
    return sender;
  }
}
