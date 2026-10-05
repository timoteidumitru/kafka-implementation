# API Gateway — Architecture Audit

**Project:** Kafka Microservices Distributed System  
**Module:** `api-gateway`  
**Phase:** Phase 0 — Architecture & Current-State Audit  
**Audit date:** 5 October 2026  
**Status:** Audit complete — findings to address during Phase 1

---

## 1. Audit purpose

This document consolidates the API Gateway findings identified during Phase 0. The purpose is to record the current implementation, mismatches, strengths, and Phase 1 actions without refactoring during the audit itself.

The review covers the Gateway module and the relevant `platform-compose.yml` deployment wiring.

## 2. Current role

The API Gateway currently provides:

- HTTP entry point on port `8080`
- explicit Order Service routing through `lb://order-service`
- Eureka-backed service resolution
- Redis-backed rate limiting for `POST /api/orders`
- correlation-ID generation/propagation
- request logging
- Actuator and Prometheus metrics
- tracing/Zipkin configuration

The intended architectural boundary remains appropriate: clients access Order through the Gateway while Payment, Inventory, and Notification remain internal Kafka-driven services.

## 3. Positive findings

### 3.1 Appropriate gateway technology

Spring Cloud Gateway/WebFlux is a suitable choice. There is no reason to replace the Gateway technology during Phase 1.

### 3.2 Service discovery integration

Using `lb://order-service` with Eureka provides service-name-based routing rather than hard-coded Order Service locations.

### 3.3 Redis rate limiting exists

The `RequestRateLimiter` implementation provides a useful distributed rate-limiting foundation.

### 3.4 Correlation ID propagation exists

`X-Correlation-Id` is generated when absent and propagated to downstream requests.

### 3.5 Observability foundations exist

Actuator, Prometheus, tracing and Zipkin-related configuration are already present and should be standardized rather than rebuilt.

### 3.6 Local startup dependency is health-aware

The Gateway waits for Eureka's Docker healthcheck through `depends_on: condition: service_healthy`, which is useful for deterministic local startup.

---

# 4. Findings

## AG-01 — Docker profile hard-coded in `application.yml`

**Priority:** High  
**Area:** Configuration

`application.yml` activates `docker`, while Compose also supplies `SPRING_PROFILES_ACTIVE=docker`.

The application should not decide that Docker is its default runtime environment.

**Phase 1:** remove the hard-coded active profile and standardize `application.yml`, `application-local.yml`, `application-docker.yml`, and eventually `application-prod.yml`. Select profiles externally.

## AG-02 — Docker-specific hostnames in base configuration

**Priority:** High  
**Area:** Configuration

`redis`, `eureka-server`, and `zipkin` are Docker-topology names currently embedded in base configuration.

**Phase 1:** move environment-specific addresses into profile-specific configuration and/or environment-variable placeholders as part of the cross-service configuration-standardization task.

## AG-03 — `EUREKA_SERVER` Compose variable is not consumed

**Priority:** High  
**Area:** Docker/configuration contract

Compose defines:

```yaml
EUREKA_SERVER: http://eureka-server:8761/eureka
```

but Gateway configuration hard-codes the same URL rather than consuming `${EUREKA_SERVER}`.

The application works because the address is duplicated, not because Compose controls the property.

**Phase 1:** establish one configuration contract. Prefer a standard Spring property/environment mapping such as `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`, or keep the Docker value in `application-docker.yml`.

## AG-04 — Discovery locator can conflict with the controlled API boundary

**Priority:** High  
**Area:** Routing/security boundary

`spring.cloud.gateway.discovery.locator.enabled=true` exists alongside explicit routes.

The intended architecture is to expose selected APIs deliberately, not automatically expose every Eureka-discovered business service.

**Phase 1:** strongly prefer explicit public routes. Eureka can still resolve `lb://order-service` without using discovery-generated public routes.

## AG-05 — `userKeyResolver` trusts an unverified `X-User-Id`

**Priority:** High / security-related  
**Area:** Identity/rate limiting

The resolver reads a caller-provided `X-User-Id`. Until authentication exists, this is not a trustworthy identity source.

It is currently unused by the reviewed Order route.

**Phase 1/security phase:** derive user identity from an authenticated principal/JWT security context. Remove or leave this resolver unused until trusted identity exists.

## AG-06 — IP rate limiting assumes remote address represents the client

**Priority:** Medium  
**Area:** Deployment/rate limiting

Using `getRemoteAddress()` is acceptable for the current local environment, but behind a trusted reverse proxy/load balancer it may identify infrastructure instead of the real client.

Blindly trusting forwarded headers would also be unsafe.

**Future action:** design production client-IP resolution around the actual trusted proxy topology.

## AG-07 — Reactive MDC handling needs redesign

**Priority:** High  
**Area:** Observability/WebFlux

`CorrelationLoggingFilter` uses ordinary SLF4J MDC within a Reactor pipeline. MDC is traditionally ThreadLocal-based, while reactive execution may switch threads.

**Phase 1:** implement Reactor/context-aware logging and align correlation IDs with Micrometer/OpenTelemetry trace/span propagation.

## AG-08 — Correlation ID is not explicitly returned to clients

**Priority:** Medium  
**Area:** Observability

The ID is propagated downstream, but the reviewed filter does not establish it as a response header.

**Phase 1:** consider returning `X-Correlation-Id` so a frontend/user-visible failure can be traced across Gateway, Order, Kafka and downstream services.

## AG-09 — GET Gateway route may not match Order Service

**Priority:** Medium  
**Area:** API contract

Gateway defines a GET route for `/api/orders/**`, while the reviewed Order controller established `POST /api/orders` and did not establish a corresponding GET endpoint.

**Phase 1:** define the intended public Order API and then make Gateway routes match it. Remove stale routes rather than creating endpoints merely to satisfy Gateway configuration.

## AG-10 — Gateway error behaviour is not standardized

**Priority:** Medium  
**Area:** API boundary

Consistent handling is not yet established for edge failures such as 429, 401/403 (future security), 503, 504, discovery/routing failures and validation errors.

**Phase 1:** define consistent edge error responses and include correlation information where useful. Business exceptions must remain owned by business services.

## AG-11 — Rate-limit mechanism exists but policy is basic

**Priority:** Medium  
**Area:** Resilience/security

The Redis implementation is a good start, but currently represents a simple IP-based order-creation policy.

A future model may distinguish anonymous/IP abuse protection, authenticated-user quotas, and endpoint-specific policies.

Do not overengineer this before authentication and real requirements exist.

## AG-12 — Authentication/authorization is intentionally missing

**Priority:** Planned major capability  
**Area:** Security

JWT/security is not yet implemented. This is known project scope rather than an accidental regression.

Future Gateway responsibilities may include authentication/token validation, authorization, trusted principal extraction and principal-based rate limiting.

The Gateway must not become a business-logic layer.

## AG-13 — Spring Cloud BOM ownership is duplicated

**Priority:** Medium  
**Area:** Maven

The Gateway imports Spring Cloud `2023.0.3` even though the root project already manages the Spring Cloud BOM.

**Phase 1:** verify inheritance and centralize shared dependency/version management in the root POM.

## AG-14 — Duplicate Actuator dependency

**Priority:** Low  
**Area:** Maven

`spring-boot-starter-actuator` is declared twice.

**Phase 1:** remove the duplicate.

## AG-15 — Review explicit transitive dependencies

**Priority:** Low  
**Area:** Maven

Dependencies such as WebFlux, SLF4J API and Micrometer Core should be reviewed against what the selected Spring starters already provide.

**Phase 1:** inspect the effective dependency tree and retain explicit dependencies where ownership is intentional.

## AG-16 — Observability dependency stack needs standardization

**Priority:** Medium  
**Area:** Observability/Maven

The Gateway includes several Micrometer/OpenTelemetry components, while other modules have shown differing tracing dependency combinations.

**Phase 1:** standardize one project-wide tracing approach: bridge, exporter, trace/span propagation, logging integration and unnecessary SDK/dependency duplication.

## AG-17 — Gateway Docker container lacks a healthcheck

**Priority:** Medium  
**Area:** Docker

Eureka has a Docker healthcheck; Gateway does not, despite exposing Actuator health.

**Phase 1:** add and standardize runtime container healthchecks while distinguishing process-running, readiness, liveness and dependency health.

## AG-18 — Startup ordering is not runtime resilience

**Priority:** Medium  
**Area:** Resilience

Waiting for Eureka during Compose startup is useful, but `depends_on` does not protect against Eureka becoming unavailable later.

**Phase 1:** verify that Gateway/service-discovery behaviour tolerates temporary Eureka outages and recovery.

## AG-19 — Zipkin and Kafka Exporter are logically observability components

**Priority:** Low  
**Area:** Deployment organization

`platform-compose.yml` currently groups Eureka, Gateway, Kafka Exporter and Zipkin.

Conceptually a cleaner ownership model is:

```text
Platform
+-- Eureka
+-- API Gateway

Observability
+-- Prometheus
+-- Grafana
+-- Alertmanager
+-- Zipkin
+-- Kafka Exporter
```

Physical relocation is optional; responsibility clarity is the primary goal.

## AG-20 — Infrastructure images use `latest`

**Priority:** Low/Medium  
**Area:** Reproducibility

Kafka Exporter and Zipkin use `latest`.

**Phase 1:** pin explicit infrastructure image versions so the same repository commit produces a more reproducible local environment.

## AG-21 — Redis wiring requires infrastructure-layer verification

**Priority:** Verification item  
**Area:** Docker networking

Redis is not defined in `platform-compose.yml`, while Gateway expects hostname `redis`.

This is valid if Redis is provided by another Compose layer and joins the same external `kafka-network`.

**Infrastructure audit:** verify the Redis service name, network membership and Gateway connectivity.

---

# 5. Cross-service Phase 1 findings reinforced by this audit

## Configuration standardization

Adopt a consistent model:

```text
application.yml
    common/environment-independent configuration

application-local.yml
    local development overrides

application-docker.yml
    Docker-specific endpoints/service names

application-prod.yml
    future production/cloud configuration
```

Profiles should be selected externally.

## Environment-variable standardization

Use consistent property contracts for Eureka, Kafka, Redis, datasource, tracing/Zipkin, service names and Spring profiles.

Avoid Compose variables that the application never reads.

## Observability standardization

Standardize correlation ID, trace ID, span ID, structured logging, Micrometer/OpenTelemetry integration, Prometheus metrics and Actuator exposure.

## Health/readiness standardization

Establish a common policy for Actuator health, Docker healthchecks, readiness/liveness and dependency-health expectations.

## Maven standardization

Centralize shared dependency/version management in the root POM where appropriate.

---

# 6. Phase 1 priority order

### Priority 1 — API boundary and configuration correctness

- AG-01 profile ownership
- AG-02 environment-specific base configuration
- AG-03 Eureka configuration mismatch
- AG-04 discovery-generated route exposure
- AG-07 reactive MDC/context handling
- AG-09 Gateway/Order API contract mismatch

### Priority 2 — Cross-service standardization

- configuration profiles
- environment variables
- observability
- Maven/BOM ownership
- health/readiness conventions

### Priority 3 — Gateway resilience and API behaviour

- consistent edge errors
- correlation response header
- rate-limit policy
- Eureka outage behaviour
- Gateway healthcheck

### Priority 4 — Security integration

- JWT/token validation
- authenticated principal
- authorization
- principal-based rate limiting
- elimination of trust in caller-provided identity headers

### Priority 5 — Cleanup/reproducibility

- duplicate/redundant dependencies
- explicit infrastructure versions
- platform/observability Compose organization

---

# 7. What should remain outside the Gateway

Do not move these concerns into the Gateway:

- Order business rules
- Payment logic
- Inventory logic
- saga business-state management
- domain persistence
- business-event ownership

The Gateway should remain focused on:

```text
routing
security
traffic control
observability
edge/API behaviour
```

---

# 8. Target direction

```text
                  Client / Frontend
                         |
                         v
                +-------------------+
                |    API Gateway    |
                |-------------------|
                | Authentication    |
                | Authorization     |
                | Rate limiting     |
                | Correlation       |
                | Tracing           |
                | Error handling    |
                | Routing           |
                +---------+---------+
                          |
                   lb://order-service
                          |
                          v
                 +----------------+
                 | Order Service  |
                 +-------+--------+
                         |
                       Kafka
                         |
              +----------+----------+
              |          |          |
              v          v          v
          Payment    Inventory  Notification
```

Eureka remains service discovery. Redis supports distributed rate limiting. Kafka remains the asynchronous business-service communication backbone. Observability infrastructure monitors the system without owning domain logic.

---

# 9. Conclusion

The API Gateway should **not be rewritten from scratch**.

Its architectural foundation is appropriate:

- Spring Cloud Gateway
- WebFlux/reactive edge layer
- Eureka discovery
- `lb://` routing
- Redis rate limiting
- correlation/tracing foundations
- Actuator/Prometheus integration

The current state is best described as:

> **A good prototype architectural foundation with incomplete production-boundary behaviour and configuration drift that should be standardized during Phase 1.**

Reviewing `platform-compose.yml` exposed an important cross-layer mismatch: Compose defines `EUREKA_SERVER`, but Gateway configuration does not consume it; the application works because the same address is independently hard-coded.

This is exactly the type of configuration drift Phase 0 is intended to uncover.

---

