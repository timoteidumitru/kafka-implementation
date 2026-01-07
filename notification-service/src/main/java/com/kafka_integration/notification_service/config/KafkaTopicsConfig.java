package com.kafka_integration.notification_service.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {

    public static final String NOTIFICATION_DLQ = "notification.events.dlq";

}

