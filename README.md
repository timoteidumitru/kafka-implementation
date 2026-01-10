# Distributed Event-Driven Microservices System

This project implements a **distributed microservices system** using **Kafka for messaging**, **Spring Boot 3**, **Java 21**, and **Docker**. It follows a **multi-module Maven architecture**, where the **parent `pom.xml`** manages all modules, dependencies, and builds.

The system currently includes:

* API Gateway
* Service Registry (Eureka)
* Order Service
* Payment Service
* Inventory Service
* Notification Service
* Shared Events Module (Base, Inventory, Order, Payment events)

All services implement **Resilience4j** (Retry, CircuitBreaker, Bulkhead), **Transactional guarantees**, and **IdempotencyGuard** for safe Kafka event processing.

---

## Key Components

### 1. API Gateway

**Purpose:** Routes requests to microservices and handles resilience patterns.

**Technology:** Spring Cloud Gateway

**Endpoints:**

* `/api/orders` → Order Service

**Resilience:** Retry, CircuitBreaker, Bulkhead applied at gateway level.

---

### 2. Service Registry

**Purpose:** Dynamic service discovery.

**Technology:** Eureka Server

**Registered Clients:** Order, Payment, Inventory, and Notification services.

---

### 3. Order Service

**Purpose:** Handles order creation and interacts with Kafka.

**Responsibilities:**

* Receive requests from API Gateway (`/api/orders`)
* Publish `order.events` to Kafka
* Consume `payment.events` for payment results:

```java
@Retry(name = "order-kafka")
@CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
@Bulkhead(name = "order-kafka")
@KafkaListener(topics = "payment.events", groupId = "order-service")
```

* Ensure **transactional integrity** and **idempotency** using `IdempotencyGuard`

---

### 4. Payment Service

**Purpose:** Validates payments and produces Kafka events.

**Responsibilities:**

* Consume `order.events`
* Validate payment via business logic & database
* Publish `payment.events`
* Supports **transactional safety** and **idempotent event processing**

**Resilience:** Retry, CircuitBreaker, Bulkhead applied at service level.

---

### 5. Inventory Service

**Purpose:** Manages stock and reserves inventory.

**Responsibilities:**

* Consume `order.events`
* Check stock availability and reserve items
* Publish `inventory.events`
* Persist inventory in MySQL

**Resilience:** Retry, CircuitBreaker, Bulkhead applied at service level.

---

### 6. Notification Service

**Purpose:** Sends notifications to users about order/payment outcomes.

**Responsibilities:**

* Consume `payment.events`
* Send email/SMS notifications

**Resilience:** Retry, CircuitBreaker, Bulkhead applied at service level.

---

### 7. Shared Events Module

**Purpose:** Defines common Kafka events across services.

**Contents:**

* Base events
* Inventory events
* Order events
* Payment events

**Kafka Topics:**

* `order.events` → order creation and updates
* `payment.events` → payment results
* `inventory.events` → inventory updates

---

### 8. Kafka

**Purpose:** Asynchronous communication between services.

**Topics:**

* `order.events`
* `payment.events`
* `inventory.events`

**Resilience:** Partitioned and replicated for fault tolerance and scalability.

---

### 9. Database (MySQL)

**Purpose:** Persistent storage for all services.

**Schema:**

* Order Service → orders table
* Payment Service → processed events, balances
* Inventory Service → product stock levels

---

### 10. Docker Containerization

**Three-layered approach:**

1. **Infrastructure:** Kafka, Zookeeper, MySQL
2. **Platform:** Service Registry, API Gateway
3. **Services:** Order, Payment, Inventory, Notification

All modules are containerized for consistent deployment across environments.

---

## Project Structure

```
kafka-implementation-master/
├── eureka-server/
├── api-gateway/
├── order-service/
├── payment-service/
├── inventory-service/
├── notification-service/
├── shared-events/   (Base, Inventory, Order, Payment events)
├── kafka-setup/     (Docker Compose for Kafka, Zookeeper, MySQL)
├── pom.xml          (parent Maven POM)
└── .gitignore
```

---

## How to Run the Project

### Prerequisites

* Java 21
* Maven
* Docker & Docker Compose

### Steps

1. Clone the repository:

```sh
git clone https://github.com/timoteidumitru/kafka-implementation.git
cd kafka-implementation
```

2. Start infrastructure containers (Kafka, Zookeeper, MySQL):

```sh
cd kafka-setup
docker-compose up -d
```

3. Build the project with Maven:

```sh
cd ..
mvn clean install
```

4. Run microservices either via IDE or Docker.

5. Access the single API endpoint:

```sh
http://localhost:8080/api/orders
```

---

## Future Enhancements

* Add **Identity API** for JWT-based authentication
* Expand **monitoring** with Prometheus and Grafana
* Add **integration tests** for all containerized services

---

