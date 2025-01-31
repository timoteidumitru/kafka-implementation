package com.kafka_implementation.order_api.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final List<String> receivedMessages = Collections.synchronizedList(new ArrayList<>());

    @Value("${notification.topic.name:notification-topic}")
    private String notificationTopic;

    public OrderConsumer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "payment-result-topic", groupId = "order-service")
    public void consumePaymentEvent(String message) {
        logger.info("Payment Event Received: {}", message);

        synchronized (receivedMessages) {
            receivedMessages.add(message);
        }

        String notificationMessage = "Order update: " + message;
        kafkaTemplate.send(notificationTopic, notificationMessage);
        logger.info("Notification Event Sent: {}", notificationMessage);
    }

    public List<String> getReceivedMessages() {
        synchronized (receivedMessages) {
            return new ArrayList<>(receivedMessages);
        }
    }
}

