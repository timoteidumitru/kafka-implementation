package com.kafka_integration.notification_api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    @GetMapping("/health")
    public String healthCheck() {
        return "Notification Service is running.";
    }
}

