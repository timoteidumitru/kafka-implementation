package com.kafka_implementation.order_service.service;

import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kafka_implementation.order_service.outbox.service.OutboxWriter;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.order.OrderCreatedEvent;

import static com.kafka_implementation.shared_events.topics.Topics.ORDER_EVENTS_V1;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final OutboxWriter outboxWriter;

    public OrderService(
            OrderRepository repository,
            OutboxWriter outboxWriter
    ) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public Order create(Order order) {

        Order saved = repository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                EventMetadata.create(
                        "order-service",
                        OrderCreatedEvent.VERSION
                ),
                saved.getId(),
                saved.getUserId(),
                saved.getProductId(),
                saved.getQuantity(),
                saved.getPrice()
        );

        outboxWriter.store(
                event.metadata().eventId(),
                ORDER_EVENTS_V1,
                event
        );

        return saved;
    }

    @Transactional
    public void cancel(UUID orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow();

        order.markCancelled();
    }

    @Transactional
    public void complete(UUID orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow();

        order.markCompleted();
    }
}
