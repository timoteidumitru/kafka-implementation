package com.kafka_integration.notification_api.controller;

import com.kafka_integration.notification_api.entity.Notification;
import com.kafka_integration.notification_api.repository.NotificationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public String getNotifications(Model model) {
        List<Notification> notifications = notificationRepository.findAll();
        model.addAttribute("notifications", notifications);
        return "notifications";
    }
}
