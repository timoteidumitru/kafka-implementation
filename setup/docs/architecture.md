```
order-platform/
│
├── pom.xml                      # Parent POM
│
├── infrastructure/
│   ├── docker-compose.yml       # Kafka, Zookeeper, Prometheus, Grafana
│   ├── kafka/
│   │   └── topics.sh
│   └── monitoring/
│       ├── prometheus.yml
│       └── grafana-dashboards/
│
├── eureka-server/
│   └── pom.xml
│
├── api-gateway/
│   └── pom.xml
│
├── shared-events/               # 🔥 CRITICAL MODULE
│   ├── pom.xml
│   └── src/main/java/
│       └── com/example/events/
│           ├── base/
│           │   ├── DomainEvent.java
│           │   ├── EventMetadata.java
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
├── notification-service/
└── docs/
    ├── architecture.md
    └── saga-flows.md
    
```