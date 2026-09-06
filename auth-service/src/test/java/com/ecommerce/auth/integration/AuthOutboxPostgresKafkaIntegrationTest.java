package com.ecommerce.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.auth.entity.AuthAuditEvent;
import com.ecommerce.auth.entity.AuthOutboxEvent;
import com.ecommerce.auth.entity.IdentityActionToken;
import com.ecommerce.auth.entity.RefreshSession;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.entity.enums.IdentityActionType;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.messaging.AuthOutboxPublisher;
import com.ecommerce.auth.repository.AuthAuditEventRepository;
import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import com.ecommerce.auth.repository.IdentityActionTokenRepository;
import com.ecommerce.auth.repository.RefreshSessionRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.service.AuthOutboxService;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises the real Flyway schema and transactional outbox against PostgreSQL and Kafka.
 *
 * <p>The Testcontainers extension skips this class before Spring starts when Docker is not
 * available, so normal local unit-test runs do not fail on developer machines without Docker.
 */
@SpringBootTest(
    // Auth registers servlet SecurityFilterChains. MOCK supplies the servlet infrastructure without
    // opening a server port, allowing this data/messaging integration test to load production config.
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.profiles.active=test",
      "spring.task.scheduling.enabled=false",
      "auth.outbox.poll-delay-ms=600000",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "auth.authorization-server.issuer=https://auth.integration.test",
      "auth.signing-key.source=GENERATED",
      "auth.signing-key.allow-ephemeral=true",
      "auth.action-token.signing-secret=auth-integration-test-action-token-secret-32-bytes",
      "auth.internal.service-token=auth-integration-test-service-token"
    })
@Testcontainers(disabledWithoutDocker = true)
class AuthOutboxPostgresKafkaIntegrationTest {

  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
  private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka-native:3.8.0");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(POSTGRES_IMAGE)
          .withDatabaseName("auth_integration")
          .withUsername("auth")
          .withPassword("auth");

  @Container static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

  @DynamicPropertySource
  static void configureContainers(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeAll
  static void createUserContactTopic() throws Exception {
    try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
      admin.createTopics(Set.of(new NewTopic(KafkaTopics.USER_CONTACT_UPDATED, 1, (short) 1)))
          .all()
          .get(15, TimeUnit.SECONDS);
    }
  }

  @Autowired private UserRepository users;
  @Autowired private AuthAuditEventRepository auditEvents;
  @Autowired private IdentityActionTokenRepository identityActions;
  @Autowired private RefreshSessionRepository refreshSessions;
  @Autowired private AuthOutboxEventRepository outboxEvents;
  @Autowired private AuthOutboxService outboxService;
  @Autowired private AuthOutboxPublisher outboxPublisher;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void migratesSchemaPersistsOutboxAndPublishesNotificationCompatibleJson() throws Exception {
    Integer migratedAuthTables =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in ('users', 'auth_outbox_events', 'refresh_sessions', 'oauth2_registered_client')
            """,
            Integer.class);
    assertThat(migratedAuthTables).isEqualTo(4);

    UUID userId = UUID.randomUUID();
    String email = "outbox-" + userId + "@example.test";
    User user =
        users.saveAndFlush(
            User.builder()
                .id(userId)
                .name("Outbox Integration User")
                .email(email)
                .emailNormalized(email)
                .passwordHash("not-a-real-password")
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(Instant.now())
                .tokenVersion(0L)
                .build());

    // These three fields are PostgreSQL INET columns. Persisting them through JPA verifies that
    // the PostgreSQL JDBC type uses an explicit inet cast instead of a varchar binding.
    AuthAuditEvent auditEvent =
        auditEvents.saveAndFlush(
            AuthAuditEvent.builder()
                .actorUserId(userId)
                .subjectUserId(userId)
                .eventType("INTEGRATION_TEST")
                .outcome(AuthAuditOutcome.SUCCESS)
                .ipAddress("192.0.2.10")
                .build());
    RefreshSession refreshSession =
        refreshSessions.saveAndFlush(
            RefreshSession.builder()
                .user(user)
                .tokenHash("refresh-" + UUID.randomUUID())
                .tokenFamilyId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .ipAddress("192.0.2.11")
                .build());
    IdentityActionToken action =
        identityActions.saveAndFlush(
            IdentityActionToken.builder()
                .user(user)
                .tokenHash("action-" + UUID.randomUUID())
                .actionType(IdentityActionType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(30)))
                .requestedIp("192.0.2.12")
                .build());
    assertThat(
            jdbcTemplate.queryForObject(
                "select host(ip_address) from auth_audit_events where id = ?",
                String.class,
                auditEvent.getId()))
        .isEqualTo("192.0.2.10");
    assertThat(
            jdbcTemplate.queryForObject(
                "select host(ip_address) from refresh_sessions where id = ?",
                String.class,
                refreshSession.getId()))
        .isEqualTo("192.0.2.11");
    assertThat(
            jdbcTemplate.queryForObject(
                "select host(requested_ip) from identity_action_tokens where id = ?",
                String.class,
                action.getId()))
        .isEqualTo("192.0.2.12");

    outboxService.enqueueUserContactUpdated(user);
    AuthOutboxEvent pending = outboxEvents.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().stream()
        .filter(event -> userId.equals(event.getAggregateId()))
        .findFirst()
        .orElseThrow();

    assertThat(pending.getTopic()).isEqualTo(KafkaTopics.USER_CONTACT_UPDATED);
    assertThat(pending.getPayload()).contains(email);

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
      consumer.subscribe(Set.of(KafkaTopics.USER_CONTACT_UPDATED));
      consumer.poll(Duration.ofSeconds(2)); // Join the consumer group before publishing.

      outboxPublisher.publishPending();

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
      assertThat(records.count()).isGreaterThan(0);

      String payload = null;
      for (var record : records.records(KafkaTopics.USER_CONTACT_UPDATED)) {
        if (userId.toString().equals(record.key())) {
          payload = record.value();
          break;
        }
      }
      assertThat(payload).isNotNull();
      JsonNode event = objectMapper.readTree(payload);
      assertThat(event.isObject()).isTrue();
      assertThat(event.path("userId").asText()).isEqualTo(userId.toString());
      assertThat(event.path("email").asText()).isEqualTo(email);
      assertThat(event.path("active").asBoolean()).isTrue();

      AuthOutboxEvent published = outboxEvents.findById(pending.getId()).orElseThrow();
      assertThat(published.getPublishedAt()).isNotNull();
      assertThat(published.getAttempts()).isZero();
    }
  }

  private Properties consumerProperties() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "auth-outbox-it-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    return properties;
  }
}
