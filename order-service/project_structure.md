```
order-service/
├── controller/
│   └── OrderController.java          ← client entry point
│
├── consumer/
│   └── OrderEventListener.java       ← reacts to payment/inventory events
│
├── producer/
│   └── OrderEventPublisher.java      ← publishes order events
│
├── service/
│   ├── OrderService.java
│   └── IdempotencyGuard.java
│
├── domain/
│   └── Order.java                    ← JPA entity
│
├── repository/
│   └── OrderRepository.java
│
├── config/
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   └── KafkaTopicsConfig.java
│
└── OrderApplication.java
```