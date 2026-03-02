# Distributed Event-Driven Microservices Platform

A production-grade distributed microservices system built with Java 21,
Spring Boot 3, Spring Cloud, Apache Kafka, Docker, Prometheus, and
Grafana.

This project demonstrates:

-   Event-driven architecture (EDA)
-   Saga-style distributed workflows
-   Resilience patterns (Retry, Circuit Breaker, Bulkhead)
-   Observability-first design
-   Full Docker-based containerization
-   Industrial-grade layered deployment structure

------------------------------------------------------------------------

# Architecture Overview

## Architectural Style

-   Microservices
-   Event-driven communication (Kafka)
-   Database-per-service
-   API Gateway pattern
-   Service discovery (Eureka)
-   Layered containerized infrastructure

------------------------------------------------------------------------

# System Layers (Industrial Deployment Model)

The system is organized into 4 logical layers:

## 1️⃣ Infrastructure Layer

Responsible for foundational services.

Includes: - Kafka - Zookeeper - MySQL (per-service databases) - Redis -
Prometheus - Grafana

## 2️⃣ Platform Layer

Core distributed system components.

Includes: - Eureka Server - API Gateway

## 3️⃣ Business Services Layer

Domain microservices.

Includes: - Order Service - Payment Service - Inventory Service -
Notification Service

## 4️⃣ Shared Contracts Layer

Shared event models (Kafka contracts). - shared-events module - Not
deployed - No Docker image

------------------------------------------------------------------------

# Kafka Messaging Architecture

## Topics

-   order.events
-   payment.events
-   inventory.events

## Dead Letter Topics (DLT)

Each topic automatically supports:

-   order.events.DLT
-   payment.events.DLT
-   inventory.events.DLT

DLTs are used when message processing fails after retries.

## Consumer Groups

-   order-service-group
-   payment-service-group
-   inventory-service-group
-   notification-service-group

Each service runs in its own consumer group for isolation.

------------------------------------------------------------------------

# Databases (Database-per-Service)

-   orders_db
-   payments_db
-   inventory_db

Each service owns its schema and data.

------------------------------------------------------------------------

# Observability Stack

Metrics Flow:

Spring Boot Actuator → Prometheus → Grafana

## Exposed Endpoints

Each service exposes:

-   /actuator/health
-   /actuator/prometheus
-   /actuator/metrics

## Grafana Access

Default:

http://localhost:3000

Default credentials:

admin / admin

------------------------------------------------------------------------

# Running the System (Docker -- Recommended)

## Step 1: Start Infrastructure

cd setup/1.infrastructure docker compose up -d

Includes Kafka, MySQL, Redis, Prometheus, Grafana.

------------------------------------------------------------------------

## Step 2: Start Platform Layer

cd ../2.platform docker compose up -d

Access:

-   Eureka: http://localhost:8761
-   Gateway: http://localhost:8080

------------------------------------------------------------------------

## Step 3: Build All Services

From project root:

mvn clean install

------------------------------------------------------------------------

## Step 4: Start Business Services

cd setup/3.services docker compose up -d

------------------------------------------------------------------------

# Local Development Setup (Without Docker)

## Requirements

-   Java 21
-   Maven 3.9+
-   Docker (only for Kafka/MySQL if not installed locally)

## Option A (Recommended Hybrid Mode)

Run infrastructure with Docker only:

docker compose -f setup/1.infrastructure/docker-compose.yml up -d

Then run services locally via IDE.

Each service must use:

SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

or for local Kafka:

SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

------------------------------------------------------------------------

# Testing the System

## Create Order

POST http://localhost:8080/api/orders

Body:

{ "productId": "P-1001", "quantity": 1, "price": 199.99 }

Flow:

1.  Order Service publishes order.events
2.  Payment Service processes payment
3.  Inventory Service reserves stock
4.  Notification Service reacts

------------------------------------------------------------------------

# Stress Testing (Load Testing)

To observe behavior under load:

## Option 1 -- Apache JMeter

Create thread group: - 100--1000 users - Ramp-up 30 seconds - Loop 10
times

Target: POST /api/orders

## Option 2 -- k6 (Recommended Modern Tool)

Example:

k6 run load-test.js

Example script:

import http from 'k6/http'; import { sleep } from 'k6';

export default function () {
http.post('http://localhost:8080/api/orders', JSON.stringify({
productId: "P-1001", quantity: 1, price: 199.99 }), { headers: {
'Content-Type': 'application/json' }, }); sleep(1); }

------------------------------------------------------------------------

# Observing System Under Stress

Monitor:

-   Grafana dashboards
-   JVM memory usage
-   Kafka consumer lag
-   Circuit breaker state
-   Database connections

Key metrics:

-   http.server.requests
-   kafka.consumer.records.lag
-   resilience4j_circuitbreaker_state
-   jvm.memory.used

------------------------------------------------------------------------

# Industrial Standards Applied

-   Separation of concerns
-   Database-per-service
-   Centralized observability
-   Docker-based reproducible environments
-   Idempotent consumers
-   Dead-letter queues
-   Resilience patterns
-   Clear layered deployment

------------------------------------------------------------------------

# Future Improvements

-   OAuth2 / JWT Identity service
-   Kubernetes deployment (Helm charts)
-   Chaos engineering tests
-   Horizontal auto-scaling

------------------------------------------------------------------------

# License

MIT License
