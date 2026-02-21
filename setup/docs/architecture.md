```
order-platform/
│
├── pom.xml                      # Parent POM
│
├── setup/
│   ├── 1.infrastructure/      
│   │    ├── mysql/
│   │    └── infra-compose.yml
│   ├── 2.platform/
│   │    └── platform-compose.yml
│   └── 3.monitoring/
│   │    ├── alertmanager/
│   │    ├── prometheus/
│   │    └── monitor-compose.yml
│   └── 4.services/
│        └── services-compose.yml
├── eureka-server/
│   └── pom.xml
│
├── api-gateway/
│   └── pom.xml
│
├── shared-events/               # 🔥 CRITICAL MODULE
│   ├── pom.xml
│   └── src/main/java/
│       └── com/kafka_implementation/shared_events/
│           ├── base/
│           │   ├── DomainEvent.java
│           │   ├── EventMetadata.java
│           │   ├── EventMetadataFactory.java
│           │   └── EventType.java
│           │
│           ├── order/
│           │   ├── OrderCreatedEvent.java
│           │   ├── OrderCompletedEvent.java
│           │   └── OrderFailedEvent.java
│           │
│           ├── payment/
│           │   ├── PaymentRequestedEvent.java
│           │   ├── PaymentCompletedEvent.java
│           │   └── PaymentFailedEvent.java
│           │
│           ├── inventory/
│           │   ├── InventoryReserveRequestedEvent.java
│           │   ├── InventoryReservedEvent.java
│           │   └── InventoryReservationFailedEvent.java
│           │
│           └── serialization/
│               └── KafkaJsonConfig.java
│
├── order-service/
├── payment-service/
├── inventory-service/
└── notification-service/
    
```