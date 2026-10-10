#  Eureka Server — Architecture Audit

**Project:** Kafka Microservices Distributed System  
**Module:** `eureka-server` / Service Registry  
**Phase:** Phase 0 — Architecture & Current-State Audit  
**Audit date:** 6 October 2026  
**Status:** Phase 0 audit complete — findings to be addressed during Phase 1

---

## 1. Executive Summary

The Eureka module is intentionally small, which is appropriate for its role. A service registry does not need the domain, repository, controller, Kafka producer/consumer, or persistence layers used by the business services.

The current implementation has the correct basic responsibility:

- runs a dedicated Eureka Server;
- listens on port `8761`;
- does not register itself as a Eureka client;
- does not fetch the registry as a client;
- exposes Actuator/Prometheus endpoints;
- runs successfully as part of the local Docker platform layer.

The main gaps are not missing application code. They are primarily **configuration ownership, environment separation, dependency standardisation, container hardening, observability consistency, and documenting the difference between the local single-node registry and a production/high-availability registry**.

Overall assessment:

> **Working development/local service registry with the correct fundamental role, but requiring configuration and operational standardisation before it should be described as production-oriented.**

---

## 2. Current Module Structure

```text
eureka-server/
├── .gitattributes
├── Dockerfile
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── kafka_implementation/
        │           └── eureka_server/
        │               └── EurekaServerApplication.java
        └── resources/
            └── application.yml
```

### Assessment

This small structure is appropriate.

Do **not** add artificial packages such as:

```text
controller/
service/
repository/
domain/
```

simply to make Eureka resemble Order, Payment, or Inventory Service.

The Service Registry belongs to the **platform/infrastructure-facing application layer**, not the business-domain layer.

---

## 3. Architectural Position

Current separation of concerns:

```text
PLATFORM
├── Eureka Server / Service Registry
└── API Gateway

OBSERVABILITY
├── Prometheus
├── Grafana
├── Alertmanager
├── Zipkin
└── Kafka Exporter
```

Eureka's responsibility should remain narrow:

```text
service instance
      │
      │ register / heartbeat
      ▼
┌───────────────────┐
│   Eureka Server   │
│ Service Registry  │
└───────────────────┘
      ▲
      │ discover
      │
API Gateway / other Eureka clients
```

Eureka should **not** become responsible for:

- authentication;
- authorization;
- API routing;
- rate limiting;
- application configuration;
- Kafka coordination;
- business health decisions;
- central logging;
- metrics storage;
- tracing storage.

Those responsibilities belong elsewhere in the architecture.

---

## 4. Files Reviewed

The audit is based on:

- `eureka-server/pom.xml`
- `EurekaServerApplication.java`
- `application.yml`
- `Dockerfile`
- the previously reviewed Eureka section of `setup/2.platform/platform-compose.yml`

Relevant Compose state:

```yaml
eureka-server:
  build: ../../eureka-server
  image: eureka-server:latest
  container_name: eureka-server
  ports:
    - "8761:8761"
  networks:
    - kafka-network
  environment:
    SPRING_PROFILES_ACTIVE: docker
  healthcheck:
    test: [ "CMD", "wget", "--spider", "-q", "http://localhost:8761/actuator/health" ]
    interval: 10s
    retries: 5
```

---

# 5. Positive Findings

## EU-P01 — Dedicated Service Registry

`@EnableEurekaServer` is correctly used:

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

This is clear and appropriately minimal.

---

## EU-P02 — Eureka Server Does Not Register Itself

Current configuration:

```yaml
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

This is correct for the current standalone registry role.

The server is not pretending to be an ordinary discovery client.

---

## EU-P03 — Conventional Eureka Port

```yaml
server:
  port: 8761
```

Using the conventional Eureka port makes the local architecture easy to understand.

---

## EU-P04 — Health and Metrics Infrastructure Exists

The module already exposes:

```yaml
health,info,metrics,prometheus
```

and has Actuator and Prometheus dependencies.

This gives Phase 1 a useful foundation rather than requiring observability to be introduced from scratch.

---

## EU-P05 — Docker Health Check Exists at Orchestration Level

The Compose layer checks:

```text
http://localhost:8761/actuator/health
```

and the API Gateway waits for Eureka health before starting.

That is better than relying solely on container process existence.

---

# 6. Phase 0 Findings

## EU-01 — Spring Cloud BOM Is Duplicated in the Child POM

**Priority:** 🟠 Medium  
**Area:** Maven / dependency governance

The root project already owns:

```xml
<spring-cloud.version>2023.0.3</spring-cloud.version>
```

and imports:

```xml
<artifactId>spring-cloud-dependencies</artifactId>
```

However, `eureka-server/pom.xml` imports the same BOM again.

### Why this matters

The purpose of the root multi-module POM is to centralise version governance.

Allowing child modules to repeat dependency-management blocks:

- creates multiple sources of truth;
- makes upgrades easier to perform incorrectly;
- increases configuration drift;
- weakens the benefit of the parent POM.

### Phase 1 action

Remove the Eureka child-level Spring Cloud BOM and inherit dependency management from the root POM.

---

## EU-02 — Child POM Should Explicitly Follow the Repository Parent Convention

**Priority:** 🟢 Low  
**Area:** Maven consistency

The current parent declaration is:

```xml
<parent>
    <groupId>com.kafka_implementation</groupId>
    <artifactId>kafka-microservices</artifactId>
    <version>1.0.0</version>
</parent>
```

Other modules are being standardised around:

```xml
<relativePath>../pom.xml</relativePath>
```

### Phase 1 action

Standardise child POM parent declarations across the reactor.

This is mainly repository clarity and consistency rather than an architectural defect.

---

## EU-03 — Docker Profile Is Activated but No Docker Profile Configuration Exists

**Priority:** 🟠 Medium  
**Area:** Configuration management

Compose activates:

```yaml
SPRING_PROFILES_ACTIVE: docker
```

but the Eureka module contains only:

```text
application.yml
```

There is no:

```text
application-docker.yml
```

### Important distinction

Activating a profile without a matching profile-specific file is not itself an error.

The issue is that the repository is moving toward an explicit configuration model where environment-specific configuration should be separated deliberately.

### Phase 1 target

```text
application.yml
    │
    ├── common/default settings
    │
    ▼
application-docker.yml
    │
    ├── Docker-network addresses
    └── Docker-specific overrides
```

Environment variables should remain available for runtime/deployment overrides.

---

## EU-04 — Configuration Is Not Parameterised Consistently

**Priority:** 🟠 Medium  
**Area:** Configuration portability

The Eureka configuration currently contains direct values such as:

```yaml
server:
  port: 8761
```

and:

```yaml
management:
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

Other modules have already started using patterns such as:

```yaml
${ENVIRONMENT_VARIABLE:default-value}
```

### Risk

Hardcoded deployment-specific values reduce portability between:

- local IDE execution;
- Docker;
- future CI;
- test environments;
- future cloud deployment.

### Phase 1 action

Define a consistent project-wide configuration policy rather than independently inventing environment-variable patterns in every module.

---

## EU-05 — Base `application.yml` Is Coupled to the Docker Zipkin Hostname

**Priority:** 🟠 Medium  
**Area:** Environment separation

Current configuration:

```yaml
endpoint: http://zipkin:9411/api/v2/spans
```

`zipkin` is a Docker-network hostname.

That makes the base configuration implicitly Docker-oriented.

### Phase 1 action

Move Docker-network addresses into the Docker profile or supply them via environment variables.

Conceptually:

```text
application.yml
└── common observability configuration

application-docker.yml
└── Docker Zipkin address
```

---

## EU-06 — 100% Trace Sampling Is a Development Setting

**Priority:** 🟠 Medium  
**Area:** Observability / production readiness

Current setting:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

This is useful in a learning/local environment because every trace is visible.

It should not become an unconditional production assumption.

### Why

At significant traffic levels, sampling every request can increase:

- tracing volume;
- network traffic;
- storage;
- observability cost;
- processing overhead.

### Phase 1 action

Keep high sampling for local development if useful, but make sampling environment-aware.

---

## EU-07 — Eureka Operational Behaviour Needs an Explicit Policy

**Priority:** 🟡 Review  
**Area:** Service discovery operations

The Eureka-specific configuration is intentionally minimal:

```yaml
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    wait-time-in-ms-when-sync-empty: 0
```

This is acceptable for the current local system.

However, Phase 1 should explicitly understand rather than blindly copy settings related to:

- service lease renewal;
- lease expiration;
- eviction;
- self-preservation;
- registry cache behaviour;
- peer replication;
- startup behaviour.

### Important recommendation

Do **not** add a large set of "production Eureka" properties simply because they appear in tutorials.

Every timing/eviction setting changes failure-detection behaviour and should be introduced only when its trade-off is understood.

---

## EU-08 — `wait-time-in-ms-when-sync-empty: 0` Needs Justification

**Priority:** 🟢 Low  
**Area:** Eureka configuration

Current configuration:

```yaml
eureka:
  server:
    wait-time-in-ms-when-sync-empty: 0
```

This should either:

1. remain because there is a deliberate local-development reason for it, with that reason understood/documented; or
2. be removed and the framework default behaviour used.

### Phase 1 action

Review this setting instead of carrying it forward as unexplained configuration.

---

## EU-09 — Health/Readiness Configuration Should Be Standardised Across Runtime Services

**Priority:** 🟠 Medium  
**Area:** Operations

Actuator health exists, and Compose uses:

```text
/actuator/health
```

The project should establish one consistent convention for:

- liveness;
- readiness;
- container health;
- dependency startup;
- future orchestration probes.

### Phase 1 action

Include Eureka in the cross-service health/readiness standardisation task.

---

## EU-10 — Docker Runtime Uses a Full JDK Image

**Priority:** 🟢 Low  
**Area:** Containers

Current base:

```dockerfile
FROM eclipse-temurin:21-jdk-jammy
```

This works correctly.

For runtime-only containers, however, a JRE/runtime-oriented image can reduce image size and attack surface.

### Phase 1 action

Review Docker base-image policy across all Java runtime modules together.

Do not optimise Eureka independently while leaving every other service inconsistent.

---

## EU-11 — Container Hardening Is Minimal

**Priority:** 🟠 Medium  
**Area:** Container security

Current Dockerfile:

```dockerfile
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/eureka-server-1.0.0.jar eureka-server.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
```

There is no explicit non-root runtime user.

### Phase 1 action

Create a project-wide Docker hardening standard covering areas such as:

- non-root execution;
- runtime image choice;
- image versioning;
- JVM runtime options;
- container health ownership;
- graceful shutdown.

---

## EU-12 — Health Check Ownership Should Be Deliberate

**Priority:** 🟢 Low  
**Area:** Docker / orchestration

The Dockerfile itself contains no `HEALTHCHECK`.

Compose provides the health check instead.

That is not inherently wrong.

### Phase 1 decision

Choose a consistent strategy:

```text
Dockerfile health check
        OR
orchestration-level health check
```

For this project, Compose-level ownership is perfectly reasonable, especially because a future orchestrator would normally own probe configuration.

The important point is consistency.

---

## EU-13 — Observability Dependency Stack Needs Cross-Service Review

**Priority:** 🟠 Medium  
**Area:** Dependency architecture

Eureka currently includes:

- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-zipkin`
- `micrometer-registry-prometheus`
- `spring-boot-starter-actuator`

These should be reviewed as part of the wider observability audit.

### Question to answer

Does every runtime module need the same tracing dependencies, or has observability configuration grown through copy/paste?

### Phase 1 action

After auditing the Observability layer, define a coherent tracing/metrics dependency strategy across services.

---

## EU-14 — Single Eureka Instance Is a Single Point of Failure

**Priority:** 🔴 Architectural / production limitation  
**Area:** Availability

Current local architecture:

```text
          ┌───────────────┐
          │ Eureka Server │
          │    single     │
          └───────────────┘
```

If that instance fails, registry availability is lost.

### Current project assessment

For the local Docker environment:

```text
one Eureka instance → acceptable
```

The project is intentionally mimicking deployment locally and is not yet claiming cloud/production HA.

Therefore Phase 1 should **not** introduce several Eureka nodes simply to make the repository look enterprise-grade.

### Production implication

A future production design would need an explicit high-availability strategy.

Potentially:

```text
Eureka A ◄──── peer replication ────► Eureka B
   ▲                                      ▲
   │                                      │
services / gateway use resilient registry configuration
```

The exact design should be decided when the project moves toward real deployment.

### Learning objective

Be able to explain:

> The local environment deliberately uses a single Eureka instance for simplicity. I recognise that this creates a service-registry SPOF and would design registry redundancy/failure handling for a production deployment.

That is stronger architectural reasoning than unnecessarily running several registry containers locally.

---

## EU-15 — Eureka Management Surface Must Not Be Treated as a Public Production Endpoint

**Priority:** 🟠 Medium  
**Area:** Security / deployment

Compose currently exposes:

```yaml
ports:
  - "8761:8761"
```

This is appropriate for local development because the Eureka dashboard is useful for inspecting registrations.

A future cloud deployment should not automatically expose the registry/dashboard publicly.

### Phase 1 / future deployment action

Document Eureka as an internal platform component.

When cloud/security work begins, review:

- network exposure;
- authentication/authorization where appropriate;
- management endpoint exposure;
- firewall/security-group rules;
- private networking.

---

# 7. Cross-Service Findings Confirmed by Eureka

The Eureka audit reinforces several findings already appearing elsewhere.

These should **not** be solved independently in every module.

## CS-CONFIG-01 — Configuration Standardisation

Target mental model:

```text
application.yml
│
├── application identity
├── common application behaviour
├── common management defaults
└── environment-neutral configuration

application-docker.yml
│
├── Docker service hostnames
├── Docker network endpoints
└── Docker-specific overrides

environment variables
│
└── deployment/runtime overrides
```

A separate file such as:

```text
application-resilience.yml
```

is **not automatically loaded merely because Resilience4j exists in the POM**.

Any separately named configuration resource must be intentionally imported/activated according to the project's chosen Spring configuration strategy.

---

## CS-MAVEN-01 — Parent POM Must Own Shared Versions

The hierarchy should be:

```text
Spring Boot parent
       │
       ▼
root kafka-microservices POM
       │
       ├── Java version
       ├── Spring Cloud version
       ├── shared dependency management
       └── shared plugin/version policy
       │
       ▼
child modules
       │
       └── declare module-specific dependencies
```

Child modules should not repeatedly re-establish version governance already owned by the root.

This finding applies beyond Eureka and was also visible in the API Gateway.

---

## CS-DOCKER-01 — Standard Java Container Policy

Phase 1 should define one common baseline for Java services:

```text
base runtime image
non-root user
JAR naming/copy strategy
JVM options
health-check ownership
graceful shutdown
image tagging
```

Avoid fixing these differently service by service.

---

## CS-OBS-01 — Observability Dependencies Need Rationalisation

The repository currently contains combinations of Micrometer, OpenTelemetry, Zipkin, Prometheus and related dependencies.

The upcoming Observability audit should establish:

```text
application instrumentation
        │
        ├── metrics → Prometheus
        │
        └── traces → tracing bridge/exporter → Zipkin
```

and identify which dependencies are actually required in each runtime module.

---

# 8. Phase 1 Action Plan for Eureka

Recommended order:

### Step 1 — Clean Maven Ownership

- remove duplicated Spring Cloud BOM;
- standardise parent `relativePath`;
- verify build from repository root.

### Step 2 — Standardise Configuration

Introduce the agreed common/profile model.

Example target:

```text
resources/
├── application.yml
└── application-docker.yml
```

Keep common Eureka behaviour in the base configuration and Docker-specific addresses in the Docker profile.

### Step 3 — Review Eureka Behaviour

Understand before changing:

- registration lifecycle;
- heartbeats;
- lease expiration;
- eviction;
- self-preservation;
- registry availability.

Do not tune properties without a failure scenario that requires them.

### Step 4 — Standardise Actuator Health

Align Eureka with the health/readiness policy used across the project.

### Step 5 — Rationalise Observability

After the Observability Phase 0 audit, return to Eureka and keep only the tracing/metrics dependencies required by the chosen project standard.

### Step 6 — Standardise Docker Runtime

Apply the cross-project Java container policy rather than creating a Eureka-specific solution.

### Step 7 — Document Production Limitation

Record explicitly:

```text
Local Docker:
single Eureka instance accepted

Production/cloud:
registry availability and redundancy require a separate design decision
```

### Step 8 — Security Review Later

When the Security/Identity and cloud phases begin:

- ensure Eureka remains internal;
- review dashboard exposure;
- review management endpoints;
- review network access.

---

# 9. Suggested Target Module

After Phase 1, the module can remain very small:

```text
eureka-server/
├── Dockerfile
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/kafka_implementation/eureka_server/
        │       └── EurekaServerApplication.java
        └── resources/
            ├── application.yml
            ├── application-docker.yml
            └── logback-spring.xml        # only if project logging policy needs it
```

The goal is **better configuration and operations**, not more Java classes.

---

# 10. Target Responsibility Boundary

After refactoring:

```text
                    CLIENT REQUESTS
                           │
                           ▼
                    ┌─────────────┐
                    │ API Gateway │
                    └──────┬──────┘
                           │
                           │ service discovery
                           ▼
                    ┌─────────────┐
                    │   Eureka    │
                    │  Registry   │
                    └─────────────┘
                       ▲   ▲   ▲
                       │   │   │
              register │   │   │ heartbeat
                       │   │   │
               ┌───────┘   │   └────────┐
               ▼           ▼            ▼
             Order       Payment     Inventory
             Service     Service      Service
```

Notification and other future discoverable runtime services can follow the same policy where appropriate.

Eureka remains a **platform component**.

---

# 11. Local vs Production Expectations

| Concern | Current local system | Production-oriented direction |
|---|---|---|
| Eureka nodes | 1 | HA strategy required |
| Dashboard | Host-accessible | Internal/restricted |
| Trace sampling | 100% | Environment/tuning dependent |
| Configuration | Mostly base YAML | Profile/runtime separation |
| Zipkin hostname | Docker hardcoded | Environment-specific |
| Container user | Default | Non-root preferred |
| Runtime image | Full JDK | Review runtime image |
| Health | Compose `/actuator/health` | Standard liveness/readiness policy |
| Dependency management | Some child duplication | Root-owned versions |
| Eureka tuning | Minimal | Scenario-driven only |

---

# 12. Final Assessment

### Architecture

**Good foundation.**

The module has a clear single responsibility and does not contain unnecessary business logic.

### Service discovery semantics

**Correct basic standalone configuration.**

`register-with-eureka: false` and `fetch-registry: false` align with the current standalone server role.

### Configuration maturity

**Needs standardisation.**

The largest immediate improvement is separating common configuration from Docker/runtime-specific configuration consistently across the repository.

### Availability

**Appropriate locally, not HA.**

The single Eureka node is intentionally acceptable for the current Docker learning/development environment, while remaining a documented production limitation.

### Observability

**Present but requires rationalisation.**

The project has a substantial observability foundation, but the upcoming Observability audit should determine whether dependency/configuration choices are coherent rather than copied across modules.

### Container maturity

**Functional, basic.**

The Dockerfile is enough to run the service but should later inherit the same hardening/runtime standards as the rest of the Java services.

---

# 13. Finding Register

| ID | Finding | Priority | Phase |
|---|---|---:|---|
| EU-01 | Duplicate Spring Cloud BOM in child POM | 🟠 Medium | Phase 1 |
| EU-02 | Parent POM declaration consistency | 🟢 Low | Phase 1 |
| EU-03 | Docker profile activated without Docker-specific configuration | 🟠 Medium | Phase 1 |
| EU-04 | Configuration parameterisation inconsistent | 🟠 Medium | Phase 1 |
| EU-05 | Base YAML coupled to Docker Zipkin hostname | 🟠 Medium | Phase 1 |
| EU-06 | 100% tracing sampling unconditional | 🟠 Medium | Phase 1 |
| EU-07 | Eureka operational behaviour requires explicit policy | 🟡 Review | Phase 1 |
| EU-08 | `wait-time-in-ms-when-sync-empty: 0` unexplained | 🟢 Low | Phase 1 |
| EU-09 | Health/readiness needs cross-service standard | 🟠 Medium | Phase 1 |
| EU-10 | Full JDK runtime image | 🟢 Low | Phase 1 |
| EU-11 | Container runs without explicit non-root policy | 🟠 Medium | Phase 1 |
| EU-12 | Health-check ownership should be standardised | 🟢 Low | Phase 1 |
| EU-13 | Observability dependency stack needs review | 🟠 Medium | Observability audit / Phase 1 |
| EU-14 | Single registry is a production SPOF | 🔴 Architectural | Future deployment |
| EU-15 | Eureka dashboard/management surface should remain internal | 🟠 Medium | Security/cloud phase |

---

# 14. Phase 0 Decision

**Eureka Server Phase 0 audit: COMPLETE.**

No immediate redesign is required before continuing the architecture audit.

The findings should be retained for Phase 1, where Eureka will be refactored together with the relevant cross-service standards rather than in isolation.

---
