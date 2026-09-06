package com.ecommerce.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({NotificationProperties.class, AuthActionDeliveryProperties.class})
public class NotificationConfiguration {}
