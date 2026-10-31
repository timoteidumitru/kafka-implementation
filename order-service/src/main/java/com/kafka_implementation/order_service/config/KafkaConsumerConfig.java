package com.kafka_implementation.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.order_service.consumer.UnsupportedEventVersionException;
import com.kafka_implementation.order_service.consumer.UnsupportedInventoryEventException;
import com.kafka_implementation.order_service.domain.InvalidOrderStateTransitionException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(
            ObjectMapper objectMapper,
            KafkaProperties kafkaProperties,
            SslBundles sslBundles
    ) {

        JsonDeserializer<Object> jsonDeserializer =
                new JsonDeserializer<>(objectMapper);

        ErrorHandlingDeserializer<Object> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        Map<String, Object> props =
                new HashMap<>(
                        kafkaProperties.buildConsumerProperties(sslBundles)
                );

        props.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            DefaultErrorHandler errorHandler
    ) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // Apply Spring Boot's Kafka listener configuration.
        // Includes concurrency, poll timeout and ack mode.
        configurer.configure(
                (ConcurrentKafkaListenerContainerFactory) factory,
                (ConsumerFactory) consumerFactory
        );

        // Preserve the application-specific retry and DLT handler.
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler(
            @Qualifier("dltKafkaTemplate")
            KafkaTemplate<String, Object> template
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, ex) -> new TopicPartition(
                                record.topic() + ".DLT",
                                record.partition()
                        )
                );

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L)
        );

        // Unsupported event types cannot become valid through retrying.
        // Preserve Spring Kafka's existing default fatal classifications.
        handler.addNotRetryableExceptions(
                UnsupportedInventoryEventException.class,
                UnsupportedEventVersionException.class,
                InvalidOrderStateTransitionException.class
        );

        return handler;

    }

}
