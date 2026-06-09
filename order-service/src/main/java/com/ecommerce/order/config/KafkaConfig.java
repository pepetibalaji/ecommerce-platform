package com.ecommerce.order.config;

import java.util.HashMap;
import java.util.Map;

package com.ecommerce.order.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
}
}