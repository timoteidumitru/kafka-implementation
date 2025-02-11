package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared.dto.PaymentResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentResultConsumer {

    private final InventoryUpdateProducer inventoryUpdateProducer;
    private final ObjectMapper objectMapper;

    public PaymentResultConsumer(InventoryUpdateProducer inventoryUpdateProducer, ObjectMapper objectMapper) {
        this.inventoryUpdateProducer = inventoryUpdateProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-result-topic", groupId = "inventory-service")
    public void handlePaymentResult(ConsumerRecord<String, String> record) {
        try {
            PaymentResultEvent paymentResult = objectMapper.readValue(record.value(), PaymentResultEvent.class);
            if (paymentResult.isApproved()) {
                PaymentResultEvent stockUpdate = new PaymentResultEvent(paymentResult.getOrderId(), true);
                inventoryUpdateProducer.sendStockUpdate(stockUpdate);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to process payment result: " + e.getMessage());
        }
    }
}