```
            Client
              |
              v
API Gateway (Spring Cloud Gateway)
|
+--> Order Service (Command)
|
+--> User Service (read)
|
+--> Inventory Service (read)
|
+--> Pricing Service (read)
              |
              v
Kafka (Event Backbone)
|
+--> Payment Service
+--> Inventory Service
+--> Notification Service
    
```