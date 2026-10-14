package com.ecommerce.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailtrapEmailProviderTest {
  @Test
  void usesTheConfiguredTemplateForTransactionalOrderEmail() throws Exception {
    NotificationProperties properties = new NotificationProperties();
    properties.getMailtrap().setApiToken("transactional-api-token");
    properties.getMailtrap().setFromEmail("orders@pepekart.test");
    properties.getMailtrap().setFromName("Pepekart");
    properties.getMailtrap().setTemplateUuids(Map.of("ORDER_RECEIVED", "order-template-id"));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://send.api.mailtrap.io/api/send"))
        .andExpect(jsonPath("$.template_uuid").value("order-template-id"))
        .andExpect(jsonPath("$.template_variables.orderId").value("order-123"))
        .andExpect(jsonPath("$.template_variables.amount").value("42.50"))
        .andExpect(jsonPath("$.template_variables.currency").value("USD"))
        .andRespond(withSuccess("{\"success\":true,\"message_ids\":[\"message-id\"]}", MediaType.APPLICATION_JSON));
    ObjectMapper mapper = new ObjectMapper();
    MailtrapEmailProvider provider = new MailtrapEmailProvider(
        properties, new NotificationEmailContent(mapper),
        new MailtrapTemplateContent(properties, mapper), builder);
    Notification notification = new Notification();
    notification.setType("ORDER_RECEIVED");
    notification.setPayload("{\"orderId\":\"order-123\",\"amount\":\"42.50\",\"currency\":\"USD\"}");

    assertThat(provider.send("customer@example.test", notification)).isEqualTo("message-id");
    server.verify();
  }
}
