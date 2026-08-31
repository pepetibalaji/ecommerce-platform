package com.ecommerce.notification.provider;

import com.ecommerce.notification.domain.Notification;

public interface EmailProvider {
  String send(String email, Notification notification) throws Exception;
}
