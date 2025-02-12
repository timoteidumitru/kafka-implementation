package com.kafka_implementation.order_api.controller;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import com.kafka_implementation.order_api.service.OrderProducer;
import com.kafka_implementation.order_api.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderProducer orderProducer;
    private OrderService orderService;

    public OrderController(OrderRepository orderRepository, OrderProducer orderProducer) {
        this.orderRepository = orderRepository;
        this.orderProducer = orderProducer;
    }

    @GetMapping
    public String listAllOrders(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "order-list";
    }

    @GetMapping("/new")
    public String showOrderForm(Model model) {
        model.addAttribute("order", new Order());
        model.addAttribute("products", orderService.getAvailableProducts());
        return "order-form";
    }

    @PostMapping
    public String createOrder(@ModelAttribute Order order, Model model) {
        try {
            orderProducer.sendOrderEvent(order);

            model.addAttribute("message", "✅ Order request sent for validation!");
            model.addAttribute("order", order);
            return "order-confirmation"; // Show confirmation page without persisting yet
        } catch (Exception e) {
            model.addAttribute("message", "❌ Order submission failed: " + e.getMessage());
            return "order-form"; // Let user retry
        }
    }

}
