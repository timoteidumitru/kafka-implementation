package com.kafka_implementation.order_api.service;

import com.kafka_implementation.shared.dto.ProductDTO;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    public OrderService(RestTemplate restTemplate, DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    public List<ProductDTO> getAvailableProducts() {
        String inventoryServiceUrl = getInventoryServiceUrl() + "/inventory/products";
        ProductDTO[] products = restTemplate.getForObject(inventoryServiceUrl, ProductDTO[].class);
        return products != null ? Arrays.asList(products) : List.of();
    }

    public void buyProduct(Long orderId, String productCode, int quantity) {
        String inventoryServiceUrl = getInventoryServiceUrl() + "/inventory/update-stock-api";
        StockUpdateRequest stockUpdateRequest = new StockUpdateRequest(orderId, productCode, -quantity);

        restTemplate.postForObject(inventoryServiceUrl, stockUpdateRequest, String.class);
    }

    private String getInventoryServiceUrl() {
        Optional<ServiceInstance> instance = discoveryClient.getInstances("inventory-api").stream().findFirst();
        return instance.map(serviceInstance -> serviceInstance.getUri().toString()).orElseThrow(
                () -> new RuntimeException("Inventory API not found!")
        );
    }
}
