package com.ecommerce.notification.config;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification")
public class NotificationProperties {
  private int maxAttempts = 5;
  private Duration retryBaseDelay = Duration.ofSeconds(30);
  private String provider = "logging-email";
  private Mailtrap mailtrap = new Mailtrap();
  private String sandboxFromEmail = "sandbox@ecommerce.local";
  private Map<UUID, String> recipientEmailOverrides = new HashMap<>();

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int v) {
    maxAttempts = v;
  }

  public Duration getRetryBaseDelay() {
    return retryBaseDelay;
  }

  public void setRetryBaseDelay(Duration v) {
    retryBaseDelay = v;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String v) {
    provider = v;
  }

  public Map<UUID, String> getRecipientEmailOverrides() {
    return recipientEmailOverrides;
  }

  public void setRecipientEmailOverrides(Map<UUID, String> v) {
    recipientEmailOverrides = v;
  }

  public Mailtrap getMailtrap() {
    return mailtrap;
  }

  public void setMailtrap(Mailtrap v) {
    mailtrap = v;
  }

  public String getSandboxFromEmail() {
    return sandboxFromEmail;
  }

  public void setSandboxFromEmail(String v) {
    sandboxFromEmail = v;
  }

  public static class Mailtrap {
    private String apiToken;
    private String fromEmail;
    private String fromName = "Ecommerce Platform";

    public String getApiToken() {
      return apiToken;
    }

    public void setApiToken(String v) {
      apiToken = v;
    }

    public String getFromEmail() {
      return fromEmail;
    }

    public void setFromEmail(String v) {
      fromEmail = v;
    }

    public String getFromName() {
      return fromName;
    }

    public void setFromName(String v) {
      fromName = v;
    }
  }
}
