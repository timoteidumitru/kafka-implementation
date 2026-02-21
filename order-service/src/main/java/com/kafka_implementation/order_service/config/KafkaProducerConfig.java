package com.kafka_implementation.order_service.config;

import com.kafka_implementation.shared_events.base.DomainEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"; // fallback

    public KafkaProducerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, DomainEvent> inventoryProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        // Docker-safe fallback
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getenv().getOrDefault("SPRING_KAFKA_BOOTSTRAP_SERVERS",
                        kafkaProperties.getBootstrapServers() != null && !kafkaProperties.getBootstrapServers().isEmpty()
                                ? String.join(",", kafkaProperties.getBootstrapServers())
                                : DEFAULT_BOOTSTRAP_SERVERS));
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false); // avoid extra headers

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, DomainEvent> inventoryKafkaTemplate() {
        return new KafkaTemplate<>(inventoryProducerFactory());
    }
}
