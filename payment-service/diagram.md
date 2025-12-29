```
        +-----------------+
        |   Order Service |
        |  (Controller)   |
        +-----------------+
                 v
         OrderCreatedEvent
                 v
        +-----------------+
        | Payment Service |
        |  (Microservice) |
        +-----------------+
                 v
        +-----------------------+
        | - Kafka Listener      |
        | - charge()            |
        | - Idempotency         |
        | - Event Publisher     |
        +-----------------------+
              |                |
              v                v
       PaymentCompleted  PaymentFailed
              |                |
              v                v
     +-----------+  +---------------+
     | Inventory |  | Notification  |
     |  Service  |  |  Service      |
     +-----------+  +---------------+
```