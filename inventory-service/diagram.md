```
flowchart LR
    A[👩‍💳 Payment Service: Check if the customer paid] --> B[📦 Inventory Service: Reserve the items]
    B --> C{✅ Were the items reserved?}
    C -->|Yes| D[🎉 Inventory Reserved: Everything is ready!]
    C -->|No| E[⚠️ Inventory Failed: Not enough items!]
    D --> F[📬 Order Service: Continue with the order]
    E --> F[📬 Order Service: Decide what to do next]

```