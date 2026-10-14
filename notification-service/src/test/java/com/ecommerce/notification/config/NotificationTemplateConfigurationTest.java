package com.ecommerce.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class NotificationTemplateConfigurationTest {
  @Test
  void bindsDefaultVerificationAndEventSpecificTemplateIds() throws Exception {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(
        new YamlPropertySourceLoader().load("notification", new ClassPathResource("application.yml")).get(0));
    environment.getPropertySources().addFirst(new MapPropertySource(
        "template-test", Map.of(
            "MAILTRAP_TEMPLATE_PASSWORD_RESET_UUID", "reset-template-id",
            "RESEND_API_KEY", "re_test",
            "RESEND_FROM_EMAIL", "hello@pepekart.example",
            "RESEND_TEMPLATE_EMAIL_VERIFICATION_ID", "resend-verify-template",
            "NOTIFICATION_SMTP_HOST", "mailpit",
            "NOTIFICATION_SMTP_PORT", "1025")));

    NotificationProperties properties =
        Binder.get(environment).bind("notification", NotificationProperties.class).get();

    assertThat(properties.getMailtrap().getTemplateUuids())
        .containsEntry("EMAIL_VERIFICATION", "31a79ec0-e191-49d6-a730-4a50b30da69d")
        .containsEntry("PASSWORD_RESET", "reset-template-id");
    assertThat(properties.getMailtrap().getSandboxInboxId()).isEqualTo(4883696L);
    assertThat(properties.getResend().getApiKey()).isEqualTo("re_test");
    assertThat(properties.getResend().getFromEmail()).isEqualTo("hello@pepekart.example");
    assertThat(properties.getResend().getTemplateIds())
        .containsEntry("EMAIL_VERIFICATION", "resend-verify-template");
    assertThat(properties.getSmtp().getHost()).isEqualTo("mailpit");
    assertThat(properties.getSmtp().getPort()).isEqualTo(1025);
    assertThat(properties.getSmtp().isAuth()).isFalse();
  }
}
