package com.kafka_implementation.inventory_api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockUpdateProducer {

    @Value("${kafka.topic.stock-update}")
    private String stockUpdateTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public StockUpdateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendStockUpdate(String message) {
        kafkaTemplate.send(stockUpdateTopic, message);
        System.out.println("Stock update sent to Kafka: " + message);
    }
}

