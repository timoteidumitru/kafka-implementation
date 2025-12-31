package com.kafka_implementation.order_service.controller;

import com.kafka_implementation.order_service.service.OrderService;
import com.kafka_implementation.shared_events.ProductDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/orders")
@Tag(name = "Order Controller", description = "APIs for managing orders")
public class OrderController {

    private final OrderProducer orderProducer;
    private final OrderService orderService;

    public OrderController(OrderProducer orderProducer, OrderService orderService) {
        this.orderProducer = orderProducer;
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get available products", description = "Returns a list of available products that can be ordered.")
    public String listAllProducts(Model model) {
        List<ProductDTO> products = orderService.getAvailableProducts();
        model.addAttribute("products", products);
        return "product-list";
    }

    @PostMapping("/buy")
    @Operation(summary = "Create an order", description = "Places an order for a specified product and quantity.")
    public String createOrder(
            @RequestParam("productCode") @Parameter(description = "The unique product code") String productCode,
            @RequestParam("quantity") @Parameter(description = "The quantity of the product to order") Integer quantity,
            Model model) {

        List<ProductDTO> products = orderService.getAvailableProducts();
        ProductDTO product = products.stream()
                .filter(e -> e.getProductCode().equals(productCode))
                .findFirst()
                .orElse(null);

        try {
            orderProducer.sendOrderEvent(productCode, quantity);
            assert product != null;
            model.addAttribute("message", "Order placed successfully for " + quantity
                    + " units of " + product.getName() + " " + product.getDescription());
            return "order-confirmation";
        } catch (Exception e) {
            model.addAttribute("message", "Order failed: " + e.getMessage());
            return "product-list";
        }
    }

    @GetMapping("/api/products")
    @Operation(summary = "Get available products (JSON)", description = "Returns available products in JSON format.")
    @ResponseBody
    public ResponseEntity<List<ProductDTO>> getProductsAsJson() {
        return ResponseEntity.ok(orderService.getAvailableProducts());
    }
}
