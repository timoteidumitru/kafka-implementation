package com.kafka_implementation.order_service.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OutboxRecoveryConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
