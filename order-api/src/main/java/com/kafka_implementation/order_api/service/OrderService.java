package com.kafka_implementation.order_api.service;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.shared.dto.ProductDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class OrderService {

    private final OrderProducer orderProducer;
    private final RestTemplate restTemplate;

    private static final String INVENTORY_API_URL = "http://inventory-api:8080/inventory/products";

    public OrderService(OrderProducer orderProducer, RestTemplate restTemplate) {
        this.orderProducer = orderProducer;
        this.restTemplate = restTemplate;
    }

    public List<ProductDTO> getAvailableProducts() {
        return List.of(Objects.requireNonNull(restTemplate.getForObject(INVENTORY_API_URL, ProductDTO.class)));
    }

    public void processOrder(Order order) {
        orderProducer.sendOrderEvent(order);
    }
}
