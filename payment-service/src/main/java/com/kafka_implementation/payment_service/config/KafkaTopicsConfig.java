package com.kafka_implementation.payment_service.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {

    public static final String ORDER_EVENTS = "order.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String INVENTORY_EVENTS = "inventory.events";

    private KafkaTopicsConfig() {}
}
