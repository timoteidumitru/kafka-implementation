package com.kafka_integration.notification_api.service.external;

import com.kafka_implementation.shared.dto.ProductDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProductService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    public ProductService(RestTemplate restTemplate, DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    public List<ProductDTO> getAvailableProducts() {
        String inventoryServiceUrl = getInventoryServiceUrl() + "/inventory/products";
        log.info("Fetching available products from: {}", inventoryServiceUrl);

        ProductDTO[] products = restTemplate.getForObject(inventoryServiceUrl, ProductDTO[].class);

        if (products != null) {
            log.info("Fetched {} products from inventory.", products.length);
            return Arrays.asList(products);
        } else {
            log.warn("No products available in inventory.");
            return List.of();
        }
    }

    private String getInventoryServiceUrl() {
        log.info("Retrieving Inventory API service URL from Discovery Client.");
        Optional<ServiceInstance> instance = discoveryClient.getInstances("inventory-api").stream().findFirst();

        return instance.map(serviceInstance -> {
            String url = serviceInstance.getUri().toString();
            log.info("Inventory API found at: {}", url);
            return url;
        }).orElseThrow(() -> {
            log.error("Inventory API not found!");
            return new RuntimeException("Inventory API not found!");
        });
    }
}
