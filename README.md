# Project Overview

This project implements a distributed microservices system using Kafka for messaging and Docker for containerization. It includes key components such as an API Gateway, Service Registry, Order API, Payment API, Notification API, and Inventory API. The system leverages MySQL as the primary database for storing orders, payments, and inventory data.

Additionally, the project has been migrated to a **multi-module Maven** approach, where the **parent `pom.xml`** manages the build and deployment of JAR files and Docker images for each module.

## Key Components

### 1. API Gateway
**Purpose:** Routes incoming requests to the appropriate microservices and manages authentication and authorization.

**Technology:** Spring Cloud Gateway with Spring Security.

**Endpoints:**
- `/orders`: Routes to Order API.
- `/payments`: Routes to Payment API.
- `/inventory`: Routes to Inventory API.

### 2. Service Registry
**Purpose:** Eureka Server for dynamic service discovery.

**Components:**
- **Eureka Server**: Centralized registry for microservices.
- **Service Clients**: Order, Payment, Notification, and Inventory APIs register with Eureka.

### 3. Order API
**Purpose:** Handles order creation and processing while interacting with Kafka.

**Responsibilities:**
- Receive order requests from the API Gateway.
- Publish `order-created` events to Kafka.
- Listen to `payment-validated` events and update order status.

### 4. Payment API
**Purpose:** Validates payments and communicates with Kafka.

**Responsibilities:**
- Consume `order-created` events.
- Validate payment using business logic and database lookup.
- Publish `payment-validated` or `payment-declined` events to Kafka.

### 5. Inventory API
**Purpose:** Manages inventory updates and ensures stock availability.

**Responsibilities:**
- Store inventory data in MySQL.
- Listen to `order-created` events to check stock availability.
- Publish `inventory-updated` events if stock levels change.

### 6. Notification API
**Purpose:** Sends notifications to users about order and payment status.

**Responsibilities:**
- Consume `payment-result` events.
- Send email or SMS notifications based on the event outcome.

### 7. Kafka
**Purpose:** Facilitates asynchronous communication between services.

**Setup:**
- **Topics:**
  - `order-topic`: Handles order creation events.
  - `payment-result-topic`: Handles payment validation results.
  - `inventory-update-topic`: Handles inventory changes.
- **Configurations:**
  - Enable partitioning for scalability.
  - Set appropriate replication for fault tolerance.

### 8. Database (MySQL)
**Purpose:** Stores persistent data for orders, payments, and inventory.

**Schema:**
- **Order API**: Stores order details (ID, user ID, status).
- **Payment API**: Stores user balances and transaction history.
- **Inventory API**: Stores productDTO stock levels.

## How to Run the Project

### Prerequisites
- Java 17 or higher
- Maven
- Docker (for running Kafka, MySQL, and microservices)

### Steps
1. Clone the repository.
   ```sh
   git clone https://github.com/timoteidumitru/kafka-implementation.git
   cd kafka-implementation
   ```
2. Navigate to the Kafka setup directory and start Kafka in a Docker container.
   ```sh
   cd kafka-setup
   docker-compose up -d
   ```
   Follow the instructions in the `kafka-setup` folder to create the necessary topics.
3. Return to the root folder and build the project.
   ```sh
   cd ..
   mvn clean package
   ```
4. Start the microservices using Docker.
   ```sh
   docker-compose up --build
   ```
5. Once the services are running, you can test the endpoints using Postman or access the interface at:
   ```sh
   http://localhost:8080
   ```

## Future Enhancements
- **Fault Tolerance**: Implement retry mechanisms and circuit breakers on API Gateway.
- **Identity API**: Secure the application with JWT authentication.
- **Monitoring & Metrics**: Integrate Grafana and Prometheus for real-time monitoring.
- **Integration Tests**: Expand containerized integration testing for all APIs.

## Project Structure
```
kafka-microservices-architecture/
├── eureka-server/
├── api-gateway/
├── order-api/
├── payment-api/
├── inventory-api/
├── notification-api/
├── kafka-setup/
├── shared/
│   └── models/ (Common DTOs like OrderEvent, PaymentResultEvent, InventoryUpdateEvent)
└── docs/
    ├── README.md
    ├── architecture-diagram.png
    └── kafka-configs.md
```

## License
This project is licensed under the MIT License.

