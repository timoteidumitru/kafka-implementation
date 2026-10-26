package com.kafka_implementation.order_service.config;

import com.kafka_implementation.shared_events.base.DomainEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"; // fallback

    public KafkaProducerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean("dltProducerFactory")
    public ProducerFactory<String, Object> dltProducerFactory(
            ObjectMapper objectMapper
    ) {
        Map<String, Object> props = new HashMap<>(
                kafkaProperties.buildProducerProperties()
        );

        // The producer settings come from Spring Boot.
        // Serializer selection is performed by the instances below.
        Map<Class<?>, Serializer<?>> serializers =
                new LinkedHashMap<>();

        // Preserve deserialization failures byte-for-byte.
        serializers.put(
                byte[].class,
                new ByteArraySerializer()
        );

        // Retain support for domain events that fail during processing.
        serializers.put(
                DomainEvent.class,
                new JsonSerializer<DomainEvent>(objectMapper)
        );

        DelegatingByTypeSerializer valueSerializer =
                new DelegatingByTypeSerializer(serializers, true);

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                valueSerializer
        );
    }

    @Bean("dltKafkaTemplate")
    public KafkaTemplate<String, Object> dltKafkaTemplate(
            @Qualifier("dltProducerFactory")
            ProducerFactory<String, Object> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, DomainEvent> orderProducerFactory() {
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
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Primary
    @Bean
    public KafkaTemplate<String, DomainEvent> orderKafkaTemplate() {
        return new KafkaTemplate<>(orderProducerFactory());
    }

    @Bean("outboxProducerFactory")
    public ProducerFactory<String, String> outboxProducerFactory() {

        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildProducerProperties());

        props.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        props.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            @Qualifier("outboxProducerFactory")
            ProducerFactory<String, String> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

}
