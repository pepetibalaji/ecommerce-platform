package com.ecommerce.notification.config;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification")
public class NotificationProperties {
  private int maxAttempts = 5;
  private Duration retryBaseDelay = Duration.ofSeconds(30);
  private String provider = "logging-email";
  private boolean outOfStockEmailEnabled;
  private Mailtrap mailtrap = new Mailtrap();
  private Resend resend = new Resend();
  private Smtp smtp = new Smtp();
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

  public boolean isOutOfStockEmailEnabled() {
    return outOfStockEmailEnabled;
  }

  public void setOutOfStockEmailEnabled(boolean v) {
    outOfStockEmailEnabled = v;
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

  public Resend getResend() {
    return resend;
  }

  public void setResend(Resend v) {
    resend = v;
  }

  public Smtp getSmtp() {
    return smtp;
  }

  public void setSmtp(Smtp v) {
    smtp = v;
  }

  public static class Smtp {
    private String host = "localhost";
    private int port = 1025;
    private String username;
    private String password;
    private boolean auth;
    private boolean starttls;
    private String fromEmail = "hello@pepekart.local";
    private String fromName = "Pepekart";

    public String getHost() { return host; }
    public void setHost(String v) { host = v; }
    public int getPort() { return port; }
    public void setPort(int v) { port = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { password = v; }
    public boolean isAuth() { return auth; }
    public void setAuth(boolean v) { auth = v; }
    public boolean isStarttls() { return starttls; }
    public void setStarttls(boolean v) { starttls = v; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String v) { fromEmail = v; }
    public String getFromName() { return fromName; }
    public void setFromName(String v) { fromName = v; }
  }

  public static class Resend {
    private String apiKey;
    private String fromEmail;
    private String fromName = "Pepekart";
    private Map<String, String> templateIds = new HashMap<>();

    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { apiKey = v; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String v) { fromEmail = v; }
    public String getFromName() { return fromName; }
    public void setFromName(String v) { fromName = v; }
    public Map<String, String> getTemplateIds() { return templateIds; }
    public void setTemplateIds(Map<String, String> v) { templateIds = v; }
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
    private Long sandboxInboxId;
    private Map<String, String> templateUuids = new HashMap<>();

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

    public Long getSandboxInboxId() {
      return sandboxInboxId;
    }

    public void setSandboxInboxId(Long v) {
      sandboxInboxId = v;
    }

    public Map<String, String> getTemplateUuids() {
      return templateUuids;
    }

    public void setTemplateUuids(Map<String, String> v) {
      templateUuids = v;
    }
  }
}
