package com.kafka_implementation.order_api.controller;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String ORDER_TOPIC = "order-topic";


    // Display all orders from the database
    @GetMapping
    public String listAllOrders(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "order-list";
    }

    // Display the order creation form
    @GetMapping("/new")
    public String showOrderForm(Model model) {
        model.addAttribute("order", new Order());
        return "order-form";
    }

    // Handle the form submission for creating a new order
    @PostMapping
    public String createOrder(@ModelAttribute Order order, Model model) {
        order.setStatus("PENDING");
        Order savedOrder = orderRepository.save(order);

        // Publish event to Kafka
        kafkaTemplate.send(ORDER_TOPIC, "Order created with ID: " + savedOrder.getId());

        model.addAttribute("message", "Order created successfully!");
        model.addAttribute("order", savedOrder);
        return "order-confirmation";
    }
}
