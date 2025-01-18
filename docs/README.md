# Project Overview

This project implements a distributed microservice system with key components such as an API Gateway, Service Registry, Order Service, Payment Service, and Kafka for robust asynchronous communication. Below is a detailed breakdown of each component.

## Key Components

### 1. API Gateway

**Purpose**: Route incoming requests to the appropriate microservices and handle authentication/authorization.

**Technology**: Spring Cloud Gateway with Spring Security.

**Endpoints**:
- `/orders`: Routes to Order Service.
- `/payments`: Routes to Payment Service.

---

### 2. Service Registry

**Purpose**: Eureka Server for dynamic service discovery.

**Components**:
- **Eureka Server**: Centralized registry for microservices.
- **Service Clients**: Order and Payment services register with Eureka.

---

### 3. Order Service

**Purpose**: Process orders and handle communication with Kafka.

**Responsibilities**:
- Receive order requests from the API Gateway.
- Publish `order-created` events to Kafka.
- Listen to `payment-validated` events and update order status.

---

### 4. Payment Service

**Purpose**: Handle payment validation and communicate with Kafka.

**Responsibilities**:
- Consume `order-created` events.
- Validate payment using internal business logic or database lookup.
- Publish `payment-validated` or `payment-declined` events to Kafka.

---

### 5. Kafka

**Purpose**: Enable asynchronous communication between services.

**Setup**:

#### Topics:
- `order-topic`: For order creation events.
- `payment-result-topic`: For payment validation results.

#### Configurations:
- Enable partitioning for scalability.
- Set appropriate replication for fault tolerance.

---

### 6. Notification Service (Optional)

**Purpose**: Send notifications to users (e.g., order confirmation, payment failure).

**Responsibilities**:
- Consume `payment-result-topic` events.
- Send email or SMS notifications.

---

### 7. Database

**Purpose**: Store persistent data for orders and payment records.

**Options**:
- **Order Service**: Stores order details (e.g., ID, user ID, status).
- **Payment Service**: Stores user balances or transaction history.

**Suggested DB**: PostgreSQL or MySQL (use Redis for caching frequently accessed data).

---

## How to Run the Project

### Prerequisites
- Java 17 or higher
- Maven
- Docker (optional, for running Kafka and database containers)

### Steps
1. Clone the repository.
   ```bash
   git clone https://github.com/timoteidumitru/kafka-implementation.git
   cd kafka-implementation
   ```
2. Build the project using Maven.
   ```bash
   mvn clean install
   ```
3. Start the Eureka Server (Service Registry).
4. Start the API Gateway, Order Service, and Payment Service in sequence.
5. Set up Kafka topics using the configuration provided.
6. Test the endpoints using Postman or a similar tool.

---

## Future Enhancements
- Implement advanced security measures (e.g., OAuth2).
- Add caching for frequently accessed data using Redis.
- Include a front-end application for better user interaction.

---

## Project Structure

```dockerfile
kafka-order-flow/
├── eureka-server/
├── api-gateway/
├── order-service/
├── payment-service/
├── notification-service/ (Optional)
├── kafka-setup/
├── shared/
│   └── models/ (Common DTOs like OrderEvent, PaymentResultEvent)
└── docs/
    ├── README.md
    ├── architecture-diagram.png
    └── kafka-configs.md

```



## License
This project is licensed under the MIT License.