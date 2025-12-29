package com.kafka_implementation.inventory_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.events.OrderEvent;
import com.kafka_implementation.events.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InventoryConsumer {

    private final InventoryProducer inventoryProducer;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    public InventoryConsumer(InventoryProducer inventoryProducer,
                             ObjectMapper objectMapper, InventoryService inventoryService) {
        this.inventoryProducer = inventoryProducer;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "payment-result-topic", groupId = "inventory-service")
    public void handlePaymentResult(ConsumerRecord<String, String> record) {
        log.info("Received message from topic: payment-result-topic, Key: {}, Partition: {}, Offset: {}",
                record.key(), record.partition(), record.offset());

        try {
            PaymentEvent paymentResult = objectMapper.readValue(record.value(), PaymentEvent.class);
            log.info("Deserialized PaymentEvent: {}", paymentResult);

            if (paymentResult.isApproved()) {
                log.info("Payment approved for Order ID: {}. Updating inventory.", paymentResult.getOrderId());

                OrderEvent orderEvent = new OrderEvent(paymentResult.getOrderId(),
                        paymentResult.getProductCode(), paymentResult.getQuantity(), "buy");
                inventoryService.updateStock(orderEvent);

                PaymentEvent stockUpdate = new PaymentEvent(paymentResult.getOrderId(),
                        paymentResult.getProductCode(), paymentResult.getQuantity(), true);
                inventoryProducer.sendStockUpdate(stockUpdate);

                log.info("Stock update event sent for Order ID: {}", paymentResult.getOrderId());
            } else {
                log.warn("Payment declined for Order ID: {}. No stock update performed.",
                        paymentResult.getOrderId());
            }
        } catch (Exception e) {
            log.error("Failed to process payment result: {}", e.getMessage(), e);
        }
    }
}
