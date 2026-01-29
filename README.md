# Distributed Event-Driven Microservices Platform

A **production-grade distributed microservices system** built with **Java 21**, **Spring Boot 3**, **Spring Cloud**, **Apache Kafka**, and **Docker**.
The project demonstrates **event-driven architecture**, **Saga-style workflows**, **resilience patterns**, and **full containerized deployment** using a **multi-module Maven setup**.

---

## High-Level Architecture

**Core principles:**

* Event-driven communication (Kafka)
* Loose coupling between services
* Fault tolerance and resilience
* Observability-first design

**System components:**

* API Gateway (single entry point)
* Eureka Service Registry
* Order Service
* Payment Service
* Inventory Service
* Notification Service
* Shared Events module (Kafka contracts)
* Kafka + Zookeeper
* MySQL (per-service persistence)
* Redis (rate limiting / resilience support)

---

## Key Components

### 1. API Gateway

**Purpose**
Acts as the **single entry point** into the system.

**Technology**
Spring Cloud Gateway (Reactive)

**Responsibilities**

* Route incoming requests to backend services
* Apply resilience patterns globally
* Integrate with Eureka for service discovery

**Exposed Endpoint**

```
POST /api/orders
```

**Resilience**

* Retry
* Circuit Breaker
* Bulkhead

---

### 2. Service Registry (Eureka)

**Purpose**
Provides **dynamic service discovery** and health awareness.

**Technology**
Spring Cloud Netflix Eureka Server

**Registered Clients**

* API Gateway
* Order Service
* Payment Service
* Inventory Service
* Notification Service

---

### 3. Order Service

**Purpose**
Handles order creation and orchestrates the business flow via events.

**Responsibilities**

* Receive requests from API Gateway
* Publish `order.events` to Kafka
* Consume `payment.events`
* Maintain transactional consistency
* Enforce idempotency

**Kafka Listener with Resilience**

```java
@Retry(name = "order-kafka")
@CircuitBreaker(name = "order-kafka", fallbackMethod = "fallback")
@Bulkhead(name = "order-kafka")
@KafkaListener(topics = "payment.events", groupId = "order-service")
```

---

### 4. Payment Service

**Purpose**
Validates and processes payments.

**Responsibilities**

* Consume `order.events`
* Execute payment logic
* Persist payment state
* Publish `payment.events`

**Guarantees**

* Transactional processing
* Idempotent event handling
* Resilience4j protection

---

### 5. Inventory Service

**Purpose**
Manages stock levels and reservations.

**Responsibilities**

* Consume `order.events`
* Validate inventory availability
* Reserve stock
* Publish `inventory.events`
* Persist inventory data in MySQL

---

### 6. Notification Service

**Purpose**
Notifies users about order and payment outcomes.

**Responsibilities**

* Consume `payment.events`
* Send notifications (email/SMS – extensible)

---

### 7. Shared Events Module

**Purpose**
Defines **Kafka event contracts** shared across services.

**Included Events**

* Base events
* Order events
* Payment events
* Inventory events

**Kafka Topics**

* `order.events`
* `payment.events`
* `inventory.events`

> This module is **not deployed** as a service and does **not** have a Docker image.

---

### 8. Messaging Layer (Kafka)

**Purpose**
Enables asynchronous, decoupled communication.

**Features**

* Topic-based messaging
* Consumer groups per service
* Fault tolerance via partitions

---

### 9. Database Layer (MySQL)

**Purpose**
Persistent storage per service.

**Databases**

* Order Service → `orders_db`
* Payment Service → `payments_db`
* Inventory Service → `inventory_db`

Each service owns its schema (Database-per-Service pattern).

---

### 10. Containerization Strategy

**Three-layer Docker Compose setup:**

1. **Infrastructure**
   Kafka, Zookeeper, MySQL, Redis
2. **Platform**
   Eureka Server, API Gateway
3. **Services**
   Business microservices

This separation allows **independent startup, scaling, and troubleshooting**.

---

## Project Structure

```
kafka-microservices/
├── setup/
│   ├── 1.infrastructure/
│   │   └── docker-compose.yml
│   ├── 2.platform/
│   │   └── docker-compose.yml
│   └── 3.services/
│       └── docker-compose.yml
├── eureka-server/
├── api-gateway/
├── order-service/
├── payment-service/
├── inventory-service/
├── notification-service/
├── shared-events/
├── pom.xml
└── .gitignore
```

---

## Getting Started (New Users)

### Prerequisites

* Java 21
* Maven 3.9+
* Docker & Docker Compose
* Postman (or curl)

---

## Running the System with Docker

### 1️⃣ Start Infrastructure

```bash
cd setup/1.infrastructure
docker compose up -d
```

Verify:

* Kafka → `localhost:9092`
* MySQL → `localhost:3306`
* Redis → `localhost:6379`

---

### 2️⃣ Start Platform Services

```bash
cd ../2.platform
docker compose up -d
```

Access:

* Eureka Dashboard → [http://localhost:8761](http://localhost:8761)
* API Gateway → [http://localhost:8080](http://localhost:8080)

---

### 3️⃣ Build & Start Microservices

```bash
mvn clean install
cd setup/3.services
docker compose up -d
```

---

## Verifying System Health

### Eureka

Visit:

```
http://localhost:8761
```

All services should appear as **UP**.

---

### Actuator Health

Each service exposes:

```
/actuator/health
```

Example:

```
http://localhost:8081/actuator/health
```

---

## Testing with Postman

### Create an Order

```
POST http://localhost:8080/api/orders
Content-Type: application/json
```

Example payload:

```json
{
  "productId": "P-1001",
  "quantity": 1,
  "price": 199.99
}
```

Expected flow:

1. API Gateway routes request
2. Order Service publishes `order.events`
3. Payment Service processes payment
4. Inventory Service reserves stock
5. Notification Service sends update

---

## Observability & Metrics (Grafana)

* Metrics exposed via **Spring Boot Actuator**
* Collected by **Prometheus**
* Visualized in **Grafana**

Typical dashboards include:

* JVM metrics
* HTTP latency
* Kafka consumer lag
* Circuit breaker states

---

## Resilience & Safety Guarantees

* Resilience4j (Retry, CircuitBreaker, Bulkhead)
* Kafka consumer idempotency
* Transactional message handling
* Database-per-service isolation

---

## Roadmap

* Identity Service (JWT / OAuth2)
* Distributed tracing (Micrometer + Tempo)
* Chaos testing
* Saga compensation flows
* Production-ready Helm charts

---

