```
    Client
        ↓
    Order Service  → OrderCreatedEvent
        ↓
    Payment Service → PaymentCompletedEvent / PaymentFailedEvent
        ↓
    Inventory Service → InventoryReservedEvent / InventoryReservationFailedEvent
        ↓
    Order Service → COMPLETE or CANCEL order
```