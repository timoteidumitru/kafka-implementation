package com.kafka_integration.notification_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared_events.serialization.EventObjectMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return EventObjectMapperFactory.get();
    }
}

