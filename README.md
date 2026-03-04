# Distributed Event-Driven Microservices Platform

### Enterprise-Oriented Architecture (Road to Production-Grade)

------------------------------------------------------------------------

## 1. Executive Summary

This project is a distributed, event-driven microservices platform built
using modern cloud-native patterns.

It is designed as a **production-style architecture**, implementing
industry best practices such as:

-   Event-driven communication (Apache Kafka)
-   Saga-style workflow orchestration (choreography-based)
-   Topic versioning & backward compatibility strategy
-   Dead Letter Topics (DLT)
-   Distributed tracing
-   Observability-first design
-   Layered Docker deployment
-   External shared Docker network
-   Logical database-per-service pattern

> ⚠️ Note: While not yet fully enterprise-grade, this system is
> intentionally structured to evolve toward production readiness.

------------------------------------------------------------------------

## 2. Technology Stack

### Core

-   Java 21
-   Spring Boot 3
-   Spring Cloud (Gateway + Eureka)
-   Apache Kafka (Confluent 7.4.1)
-   MySQL 8
-   Redis 7

### Observability

-   Prometheus
-   Alertmanager
-   Grafana
-   Zipkin
-   Kafka Exporter

### Infrastructure

-   Docker
-   Docker Compose (Layered Deployment)
-   External Docker bridge network (`kafka-network`)

------------------------------------------------------------------------

## 3. System Architecture

### Business Services
```
Service                Port   Responsibility
---------------------- ------ -----------------------------------
order-service          8081   Order creation & event publishing
payment-service        8082   Payment processing
notification-service   8083   Event-driven notifications
inventory-service      8084   Stock validation & deduction
```
Each service:

-   Registers with Eureka
-   Connects to Kafka (`kafka:9092`)
-   Uses its own logical MySQL database
-   Exposes `/actuator/prometheus`
-   Implements versioned Kafka topic contracts

------------------------------------------------------------------------

## 4. Event Flow (Saga Choreography)

Order → Payment → Inventory → Notification

1.  Order Created → `order.events.v1`
2.  Payment Processed → `payment.events.v1`
3.  Inventory Reserved → `inventory.events.v1`
4.  Notification Sent → `notification.events.v1`

Failure handling: - Dead Letter Topics (`*.DLT`) - Consumer group
isolation - Idempotent processing pattern

------------------------------------------------------------------------

## 5. Kafka Design

### Versioned Topics

-   order.events.v1
-   order.events.v1.DLT
-   payment.events.v1
-   inventory.events.v1
-   notification.events.v1

### Consumer Groups

-   order-service-group
-   payment-service-group
-   inventory-service-group
-   notification-service-group

### Best Practices Applied

-   Topic versioning strategy
-   Dead Letter Topics
-   Explicit topic creation via `kafka-init`
-   Auto topic creation disabled
-   Kafka lag monitoring

------------------------------------------------------------------------

## 6. Docker Layered Deployment Model

The platform is split into 4 independent layers:
```
setup/ 
    ├── 1.infrastructure/ 
    ├── 2.platform/ 
    ├── 3.monitoring/ 
    └── 4.services/
```
All layers communicate through:

External Docker Network: `kafka-network`

------------------------------------------------------------------------

### 6.1 Infrastructure Layer

Includes:

-   Zookeeper
-   Kafka
-   kafka-init (topic creation script)
-   MySQL (multi-database instance)
-   Redis

Features:

-   Persistent volumes
-   Health checks
-   Explicit topic provisioning
-   Production-style separation of infrastructure

------------------------------------------------------------------------

### 6.2 Platform Layer

Includes:

-   Eureka Server → http://localhost:8761
-   API Gateway → http://localhost:8080
-   Zipkin → http://localhost:9411
-   Kafka Exporter → http://localhost:9308/metrics

Responsibilities:

-   Service discovery
-   Centralized routing
-   Distributed tracing
-   Kafka metrics exposure

------------------------------------------------------------------------

### 6.3 Monitoring Layer

Includes:

-   Prometheus → http://localhost:9090
-   Alertmanager → http://localhost:9093
-   Grafana → http://localhost:3000 (admin/admin)

Monitored Metrics:

-   http.server.requests
-   kafka_consumer_lag
-   jvm.memory.used
-   resilience4j_circuitbreaker_state

------------------------------------------------------------------------

### 6.4 Business Services Layer

Built via:

mvn clean install

Deployed via:

docker compose -f services-compose.yml up -d

------------------------------------------------------------------------

## 7. Startup Order (Critical)

1️⃣ Infrastructure\
2️⃣ Platform\
3️⃣ Monitoring\
4️⃣ Services

This order ensures Kafka and databases are ready before services
register and consume.

------------------------------------------------------------------------

## 8. Observability Strategy

This system follows an **Observability-First Approach**:

### Metrics

-   JVM metrics
-   HTTP request metrics
-   Kafka consumer lag
-   Resilience4j state transitions

### Tracing

-   Zipkin distributed tracing
-   Propagated trace IDs across services

### Monitoring Patterns

-   Latency tracking
-   Throughput monitoring
-   Error rate visualization
-   Kafka lag detection

------------------------------------------------------------------------

## 9. Load & Stress Testing

k6 script supported:

k6 run load-test.js

Expected Behavior Under Load:

-   Increased latency
-   Kafka lag growth
-   Higher DB connection usage
-   Circuit breaker state transitions

------------------------------------------------------------------------

## 10. Local Development Strategy

Recommended Hybrid Mode:

-   Infrastructure + Monitoring in Docker
-   Services running in IDE

Environment variable override:

SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

This allows debugging with real infrastructure dependencies.

------------------------------------------------------------------------

## 11. Production Readiness Roadmap

Planned improvements toward enterprise-grade:

-   Schema Registry integration
-   Avro or Protobuf event contracts
-   CI/CD pipeline (GitHub Actions)
-   Blue/Green or Canary deployments
-   Centralized configuration server
-   Kubernetes migration
-   Proper secret management (Vault)
-   Multi-node Kafka cluster
-   Horizontal scaling of services
-   Database per container (true isolation)

------------------------------------------------------------------------

## 12. Architecture Principles Applied

-   Loose coupling via events
-   Independent deployability
-   Failure isolation
-   Observability-first design
-   Explicit infrastructure provisioning
-   Backward-compatible messaging contracts

------------------------------------------------------------------------

## 13. Contribution Guidelines

1.  Maintain topic versioning compatibility
2.  Avoid breaking event contracts
3.  Ensure Docker builds succeed
4.  Run `mvn clean verify`
5.  Validate Prometheus metrics exposure
6.  Maintain health checks

------------------------------------------------------------------------

## 14. License

MIT

------------------------------------------------------------------------

## Final Note

This platform is intentionally structured as a **learning-to-enterprise
progression project**.

It reflects real-world architectural patterns used in production systems
while remaining practical for local development and experimentation.

The goal is continuous evolution toward full production-grade maturity.
