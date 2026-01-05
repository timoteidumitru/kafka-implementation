package com.kafka_implementation.payment_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(ObjectMapper objectMapper) {

        JsonDeserializer<Object> jsonDeserializer =
                new JsonDeserializer<>(objectMapper);
        jsonDeserializer.addTrustedPackages("*");

        ErrorHandlingDeserializer<Object> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        Map<String, Object> props = new HashMap<>();

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler errorHandler
    ) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(3);

        return factory;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        template,
                        (ConsumerRecord<?, ?> record, Exception ex) ->
                                new TopicPartition(
                                        record.topic() + ".DLT",
                                        record.partition()
                                )
                );

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3)
        );
    }
}
