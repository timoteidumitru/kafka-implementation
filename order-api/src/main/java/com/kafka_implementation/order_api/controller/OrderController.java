package com.kafka_implementation.order_api.controller;

import com.kafka_implementation.order_api.entity.Order;
import com.kafka_implementation.order_api.repository.OrderRepository;
import com.kafka_implementation.order_api.service.OrderProducer;
import org.springframework.beans.factory.annotation.Autowired;
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
    private OrderProducer orderProducer;

    @GetMapping
    public String listAllOrders(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "order-list";
    }

    @GetMapping("/new")
    public String showOrderForm(Model model) {
        model.addAttribute("order", new Order());
        return "order-form";
    }

    @PostMapping
    public String createOrder(@ModelAttribute Order order, Model model) {
        order.setStatus("PENDING");
        Order savedOrder = orderRepository.save(order);

        orderProducer.sendOrderEvent(savedOrder.getId());

        model.addAttribute("message", "Order created successfully!");
        model.addAttribute("order", savedOrder);
        return "order-confirmation";
    }
}
