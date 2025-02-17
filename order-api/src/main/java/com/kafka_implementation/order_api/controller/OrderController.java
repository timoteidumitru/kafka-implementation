package com.kafka_implementation.order_api.controller;

import com.kafka_implementation.order_api.service.OrderProducer;
import com.kafka_implementation.order_api.service.OrderService;
import com.kafka_implementation.shared.dto.ProductDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderProducer orderProducer;
    private final OrderService orderService;

    public OrderController(OrderProducer orderProducer, OrderService orderService) {
        this.orderProducer = orderProducer;
        this.orderService = orderService;
    }

    @GetMapping
    public String listAllProducts(Model model) {
        List<ProductDTO> products = orderService.getAvailableProducts();
        model.addAttribute("products", products);
        System.out.println(products.stream().toList());
        return "product-list";
    }

    @PostMapping("/buy")
    public String createOrder(@RequestParam("productCode") String productCode,
                              @RequestParam("quantity") Integer quantity,
                              Model model) {
        List<ProductDTO> products = orderService.getAvailableProducts();
        ProductDTO product = products.stream().filter(e -> e.getProductCode().equals(productCode)).findFirst().orElse(null);
        try {
            orderProducer.sendOrderEvent(productCode, quantity);
            assert product != null;
            model.addAttribute("message", "Order placed successfully for "
                    + quantity + " units of " + product.getName() + " " + product.getDescription());
            return "order-confirmation";
        } catch (Exception e) {
            model.addAttribute("message", "Order failed: " + e.getMessage());
            return "product-list";
        }
    }

}

