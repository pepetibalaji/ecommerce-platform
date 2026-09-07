package com.ecommerce.auth.config;

import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AuthOutboxMetrics {
  public AuthOutboxMetrics(AuthOutboxEventRepository events, MeterRegistry meters) {
    Gauge.builder("auth_outbox_pending", events, AuthOutboxEventRepository::countByPublishedAtIsNullAndDeadLetteredAtIsNull).register(meters);
    Gauge.builder("auth_outbox_dead_lettered", events, AuthOutboxEventRepository::countByDeadLetteredAtIsNotNull).register(meters);
  }
}
