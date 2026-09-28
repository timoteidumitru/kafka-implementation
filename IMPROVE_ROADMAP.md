# Order Platform --- Industry-Readiness Roadmap

**Timeline:** 1 October 2026 → 28 February 2027\
**Project:** `order-platform`\
**Primary goal:** Evolve the existing distributed-system project into a
coherent, testable, secure, observable and deployable production-style
system while strengthening the Java/backend knowledge required to
explain and defend the architecture in interviews.

> **North Star:** Do not add technology because it is fashionable or
> common in job descriptions. Every technology, pattern and abstraction
> must solve a concrete problem in this system.

------------------------------------------------------------------------

## 1. Starting Point

The project already has a meaningful distributed-systems foundation. The
goal of this roadmap is **not** to rebuild it from scratch or
continuously add technologies.

The goal is to verify the current architecture, fix genuine weaknesses,
deepen the implementation, test failure behaviour and make the system
something that can be confidently explained from both an engineering and
interview perspective.

Current known foundation includes:

-   Java 21
-   Spring Boot 3.3.3
-   Multi-module Maven repository
-   Eureka service discovery
-   Spring Cloud Gateway
-   Order Service
-   Payment Service
-   Inventory Service
-   Notification Service
-   `shared-events`
-   Kafka / Zookeeper
-   MySQL database-per-service
-   Redis
-   Docker / Docker Compose
-   Prometheus / Grafana
-   OpenTelemetry / Zipkin
-   Correlation IDs / tracing
-   Idempotency work
-   Retry / backoff
-   Circuit breakers / bulkheads
-   Versioned Kafka event/topic work
-   Four local deployment layers:
    1.  Infrastructure
    2.  Platform
    3.  Monitoring
    4.  Services

The repository itself becomes the **source of truth** from 1 October
onward. Historical discussions are useful context, but the audit must
verify what is actually implemented now.

------------------------------------------------------------------------

# 2. Working Principles

Throughout this roadmap:

1.  **Understand before changing.**
2.  **Correctness before scaling.**
3.  **Measure before optimising.**
4.  **Solve actual problems before adding technology.**
5.  **Failure paths matter as much as happy paths.**
6.  **Observability must help diagnose problems, not merely decorate
    Grafana.**
7.  **A pattern is not complete until its trade-offs are understood.**
8.  **Prefer one well-understood implementation over several partially
    implemented technologies.**
9.  **Do not introduce infrastructure simply because large companies use
    it.**
10. **Every major architectural decision should eventually be
    explainable without relying on the source code.**

------------------------------------------------------------------------

# 3. Five-Month Overview

  -----------------------------------------------------------------------
  Period                  Phase                   Main Outcome
  ----------------------- ----------------------- -----------------------
  1--18 Oct 2026          Phase 0 ---             Verified current-state
                          Architecture Audit      architecture and
                                                  prioritised findings

  19 Oct--15 Nov          Phase 1 --- Backend     Kafka reliability,
                          Reliability             configuration cleanup,
                                                  Outbox, idempotency,
                                                  DLT, Redis consistency

  16--30 Nov              Phase 2 --- Testing &   Testcontainers and
                          Failure Engineering     repeatable
                                                  happy/failure paths

  December                Phase 3 --- Security    OAuth2/OIDC/JWT,
                                                  authorization and trust
                                                  boundaries

  January                 Phase 4/5 ---           Demonstrable system and
                          Frontend + CI/CD        reproducible delivery
                                                  pipeline

  1--14 Feb 2027          Phase 6 --- Controlled  Small AWS deployment if
                          Cloud Deployment        readiness gates pass

  15--28 Feb              Phase 7 --- Production  Load, SQL, JVM,
                          Hardening               profiling, resilience
                                                  and final architecture
                                                  case study
  -----------------------------------------------------------------------

The dates are guides rather than deadlines. If the audit reveals an
important correctness problem, fixing it takes priority over maintaining
an artificial schedule.

------------------------------------------------------------------------

# 4. Phase 0 --- Architecture Audit

**Target:** 1--18 October 2026

## Objective

Before implementing improvements, establish a verified baseline of the
current system.

We want to answer:

-   What exists?
-   Why does it exist?
-   Is the implementation correct?
-   Is the responsibility in the right place?
-   Is there unnecessary coupling?
-   What happens when dependencies fail?
-   Is the implementation testable?
-   Is it observable?
-   Is it production-oriented or merely functional?
-   What should be changed now?
-   What should deliberately be postponed?

## Finding Classification

Every meaningful audit finding should be placed into one of these
categories.

### KEEP

The design is sound and solves a real problem.

Document it and leave it alone.

### IMPROVE

The direction is correct, but implementation/configuration could be made
more production-oriented.

### FIX

There is a correctness issue, architectural mismatch, unnecessary
coupling or operational problem that should be addressed.

### LATER

The improvement has value but is premature relative to the current
maturity of the project.

### DON'T ADD

The technology/pattern adds complexity without solving a concrete
current problem.

------------------------------------------------------------------------

## 4.1 Repository & Build Architecture

Review:

-   [ ] Root `pom.xml`
-   [ ] Maven module structure
-   [ ] Dependency management
-   [ ] Spring Boot version consistency
-   [ ] Java version consistency
-   [ ] Maven plugin configuration
-   [ ] Build lifecycle
-   [ ] Profiles
-   [ ] Shared dependencies
-   [ ] Module boundaries
-   [ ] `shared-events`
-   [ ] Package organisation
-   [ ] Docker build relationship with Maven artifacts

Questions:

-   Are modules genuinely independent where they should be?
-   Is too much implementation shared between services?
-   Does `shared-events` contain contracts rather than business logic?
-   Are dependency versions centrally controlled?
-   Can `mvn clean verify` build the complete repository reliably?

------------------------------------------------------------------------

## 4.2 Infrastructure Layer

Review:

``` text
setup/1.infrastructure/
```

Including:

-   [ ] Kafka
-   [ ] Zookeeper
-   [ ] MySQL
-   [ ] Redis
-   [ ] Docker networks
-   [ ] Volumes
-   [ ] Topic creation
-   [ ] Persistence
-   [ ] Environment variables
-   [ ] Health checks

Questions:

-   What survives container restart?
-   How are Kafka topics created?
-   Are topic names/versioning consistent?
-   Is Kafka auto-topic creation disabled intentionally?
-   How are databases initialised?
-   What happens if infrastructure starts in the wrong order?
-   Are credentials embedded in Compose?
-   Is Redis being used for the correct responsibilities?

------------------------------------------------------------------------

## 4.3 Platform Layer

Review:

``` text
setup/2.platform/
```

### Eureka

-   [ ] Service registration
-   [ ] Health behaviour
-   [ ] Service discovery
-   [ ] Hostname/network configuration
-   [ ] Failure behaviour

### API Gateway

-   [ ] Public routing
-   [ ] Service discovery integration
-   [ ] Rate limiting
-   [ ] Redis integration
-   [ ] Correlation ID handling
-   [ ] Resilience
-   [ ] Error handling
-   [ ] Future security boundary

Questions:

-   Which services should actually be publicly reachable?
-   Should internal services expose REST endpoints?
-   What belongs at the Gateway and what belongs in services?
-   What happens when a downstream service is unavailable?

------------------------------------------------------------------------

## 4.4 Monitoring & Observability

Review:

``` text
setup/3.monitoring/
```

Including:

-   [ ] Prometheus
-   [ ] Grafana
-   [ ] Actuator
-   [ ] OpenTelemetry
-   [ ] Zipkin
-   [ ] Correlation IDs
-   [ ] Trace propagation
-   [ ] Kafka metrics
-   [ ] JVM metrics
-   [ ] DB connection-pool metrics
-   [ ] Redis metrics
-   [ ] Business metrics

The key question is not:

> "Do we have Grafana?"

It is:

> "Can we use telemetry to explain why an order failed or became slow?"

------------------------------------------------------------------------

# 5. Service-by-Service Audit

Each service should be reviewed using the same structure.

``` text
service
│
├── pom.xml
├── Dockerfile
├── application configuration
├── configuration/
├── controller/API
├── application/service layer
├── domain/model
├── repository
├── event producers
├── event consumers
├── transactions
├── idempotency
├── resilience
├── exception handling
├── observability
└── tests
```

For every service we examine:

1.  Responsibility
2.  Data ownership
3.  API/event contracts
4.  Transaction boundaries
5.  Happy path
6.  Failure path
7.  Retry behaviour
8.  Duplicate handling
9.  Coupling
10. Testability
11. Observability
12. Operational recovery

------------------------------------------------------------------------

## 5.1 Order Service

Review:

-   [ ] `/api/orders` REST boundary
-   [ ] Order creation
-   [ ] Order state machine/status transitions
-   [ ] MySQL transaction boundaries
-   [ ] Event publication
-   [ ] Event consumption
-   [ ] Failure/compensation handling
-   [ ] Idempotency
-   [ ] Validation
-   [ ] Exception handling
-   [ ] Repository behaviour
-   [ ] Tests

Important question:

> What happens if the Order database commits successfully but Kafka
> publication fails?

------------------------------------------------------------------------

## 5.2 Payment Service

Review:

-   [ ] Order event consumption
-   [ ] Payment processing boundary
-   [ ] Payment persistence
-   [ ] Payment event publication
-   [ ] Idempotency
-   [ ] Retry behaviour
-   [ ] Circuit breaker behaviour
-   [ ] Duplicate charge protection
-   [ ] Transaction boundaries
-   [ ] Failure events
-   [ ] Tests

Important question:

> How do we guarantee that retrying a failed workflow does not charge
> the customer twice?

------------------------------------------------------------------------

## 5.3 Inventory Service

Review:

-   [ ] Inventory ownership
-   [ ] `findByProductId`
-   [ ] Pessimistic locking
-   [ ] Reservation logic
-   [ ] Release/compensation
-   [ ] Redis cache
-   [ ] Cache invalidation
-   [ ] Event consumers
-   [ ] Event producers
-   [ ] Concurrent reservations
-   [ ] Idempotency
-   [ ] Tests

Important question:

> If 1,000 orders attempt to reserve the same product simultaneously,
> what guarantees correctness?

------------------------------------------------------------------------

## 5.4 Notification Service

Review:

-   [ ] Event consumption
-   [ ] Notification responsibility
-   [ ] Persistence, if still used
-   [ ] Retry behaviour
-   [ ] Failure handling
-   [ ] Duplicate handling
-   [ ] Whether notification failure should affect the business
    transaction
-   [ ] Tests

Important question:

> Should an email/notification failure ever cause an otherwise completed
> order to fail?

------------------------------------------------------------------------

# 6. Cross-Service Workflow Audit

After reviewing individual services, stop looking at them independently.

Trace:

``` text
POST /api/orders
        │
        ▼
   API Gateway
        │
        ▼
  Order Service
        │
        ├── MySQL
        │
        └── Kafka
              │
              ▼
       Payment Service
              │
              ├── MySQL
              └── Kafka
                    │
                    ▼
             Inventory Service
                    │
                    ├── MySQL
                    ├── Redis
                    └── Kafka
                          │
                          ▼
                     Order Service
                          │
                          ▼
                      COMPLETED
```

The actual sequence must be updated to match the real implementation
discovered during the audit.

## Failure Questions

For the complete workflow:

-   [ ] What if Payment crashes?
-   [ ] What if Kafka becomes unavailable?
-   [ ] What if Inventory receives the same event twice?
-   [ ] What if Redis contains stale data?
-   [ ] What if MySQL commits but Kafka publishing fails?
-   [ ] What if Kafka publishes and the producer crashes immediately
    afterwards?
-   [ ] What if a consumer crashes after business processing but before
    acknowledgement?
-   [ ] What if a malformed event arrives?
-   [ ] What if one consumer is significantly slower than producers?
-   [ ] What if a service restarts while messages are pending?
-   [ ] What if the DB connection pool is exhausted?
-   [ ] What if retries create a retry storm?

For every important failure record:

``` text
Expected behaviour
        ↓
Observed behaviour
        ↓
Data/event state
        ↓
Recovery mechanism
```

------------------------------------------------------------------------

# 7. Phase 0 Deliverables

By the end of the audit:

-   [ ] Current-state architecture diagram
-   [ ] Verified repository/module map
-   [ ] Service-by-service findings
-   [ ] KEEP / IMPROVE / FIX / LATER / DON'T ADD register
-   [ ] Confirmed end-to-end event sequence
-   [ ] Prioritised architecture risks
-   [ ] Testing gaps
-   [ ] Security gaps
-   [ ] Deployment gaps
-   [ ] Observability gaps
-   [ ] Explicit list of technologies/patterns we decided **not** to
    introduce
-   [ ] Revised backlog for the remainder of this roadmap

------------------------------------------------------------------------

# 8. Phase 1 --- Backend Reliability

**Target:** approximately 19 October -- 15 November

The exact work depends on Phase 0 findings.

Expected focus:

## Architecture & Configuration Cleanup

-   [ ] Standardise Kafka producer/consumer configuration
-   [ ] Standardise event serialization
-   [ ] Resolve `KafkaTemplate` type inconsistencies
-   [ ] Consolidate topic configuration
-   [ ] Confirm versioned topic naming
-   [ ] Resolve application/profile naming inconsistencies
-   [ ] Remove obsolete shared modules/configuration
-   [ ] Document service responsibilities

## Transactional Outbox

For at least one critical path:

``` text
Business Transaction
       │
       ├── save business state
       │
       └── save OutboxEvent
               │
             COMMIT
               │
               ▼
        Outbox Publisher
               │
               ▼
             Kafka
```

Tasks:

-   [ ] Implement Outbox
-   [ ] Define publication status/retry behaviour
-   [ ] Handle duplicate publication safely
-   [ ] Test Kafka outage
-   [ ] Test process restart
-   [ ] Document trade-offs

## Idempotency

-   [ ] Define event identity
-   [ ] Define processed-event storage
-   [ ] Make critical consumers idempotent
-   [ ] Test duplicate delivery
-   [ ] Ensure retry does not duplicate side effects

## Retry / DLT

-   [ ] Distinguish transient from permanent failures
-   [ ] Define retry count/backoff
-   [ ] Prevent retry storms
-   [ ] Define DLT naming
-   [ ] Preserve useful event metadata
-   [ ] Define inspection/replay procedure

------------------------------------------------------------------------

# 9. Redis & Data Consistency

Principle:

> Redis accelerates suitable reads. MySQL remains authoritative for
> critical stock-changing operations unless an explicit alternative
> consistency model is designed.

Typical read:

``` text
InventoryService
       │
       ▼
     Redis
    /     \
  HIT     MISS
   │        │
return     MySQL
             │
             ▼
        populate cache
             │
             ▼
           return
```

Stock reservation:

``` text
Reserve Inventory
       │
       ▼
InventoryService
       │
       ▼
MySQL Transaction
       │
PESSIMISTIC_WRITE
       │
       ▼
Update Stock
       │
     COMMIT
       │
       ▼
Update / Invalidate Cache
```

Tasks:

-   [ ] Cache-aside
-   [ ] TTL strategy
-   [ ] Cache invalidation
-   [ ] Stale cache behaviour
-   [ ] Redis outage fallback
-   [ ] Cache stampede
-   [ ] Cache penetration
-   [ ] Staggered expiry
-   [ ] Pre-warming where justified
-   [ ] Decide whether distributed locking is actually necessary

------------------------------------------------------------------------

# 10. Phase 2 --- Testing & Failure Engineering

**Target:** approximately 16--30 November

Use **Testcontainers** so important integration tests do not depend on
manually installed local infrastructure.

Infrastructure:

-   [ ] MySQL
-   [ ] Kafka
-   [ ] Redis

Important tests:

-   [ ] `createOrder_shouldPublishOrderCreatedEvent`
-   [ ] Payment processing happy path
-   [ ] Inventory reservation happy path
-   [ ] Duplicate event does not duplicate side effects
-   [ ] Insufficient inventory triggers correct failure/compensation
-   [ ] Payment failure triggers correct workflow
-   [ ] Redis miss loads MySQL
-   [ ] Redis invalidation/update works
-   [ ] Consumer failure follows retry/DLT policy
-   [ ] Outbox survives Kafka outage
-   [ ] Relevant service restart scenarios

## Definition of Done

-   [ ] At least one complete happy path is repeatable automatically
-   [ ] At least one important failure path is repeatable automatically
-   [ ] Critical tests use real containerised dependencies
-   [ ] Duplicate delivery behaviour is proven by tests

------------------------------------------------------------------------

# 11. Phase 3 --- Identity & Security

**Target:** December 2026

Do not invent custom authentication protocols or cryptography.

Learn and implement standard security architecture.

Topics:

-   Spring Security
-   OAuth 2.0
-   OpenID Connect
-   JWT
-   Resource Server
-   Authorization
-   Roles / authorities
-   Access tokens
-   Refresh tokens
-   Claims
-   Token expiry
-   CORS
-   CSRF
-   Password hashing
-   Service-to-service trust

Conceptual architecture:

``` text
                Identity Provider
                       │
                  OAuth2 / OIDC
                       │
                       ▼
Browser ─────────► API Gateway
                       │
                token validation
                       │
                       ▼
                Public Resource
```

Tasks:

-   [ ] Map trust boundaries
-   [ ] Implement authentication
-   [ ] Implement JWT validation
-   [ ] Protect public API
-   [ ] Add roles/authorities
-   [ ] Test missing token
-   [ ] Test malformed token
-   [ ] Test expired token
-   [ ] Test insufficient permissions
-   [ ] Externalise secrets
-   [ ] Define service-to-service trust separately from user
    authentication
-   [ ] Document security architecture

Questions we must be able to answer:

-   Authentication vs authorization?
-   OAuth2 vs OIDC?
-   Access token vs refresh token?
-   Why is JWT signing important?
-   Why is a signed JWT not necessarily encrypted?
-   Where should tokens be validated?
-   Why should every microservice not implement login?
-   CORS vs CSRF?

------------------------------------------------------------------------

# 12. Phase 4 --- Small React Frontend

**Target:** January 2027

The frontend exists primarily to **demonstrate and exercise the
backend**.

Do not turn this into another major frontend portfolio project.

Suggested screens:

## Login

-   [ ] Authentication flow

## Products / Inventory

-   [ ] Product information
-   [ ] Available stock where appropriate

## Create Order

-   [ ] Product
-   [ ] Quantity
-   [ ] Submit

## Orders

Statuses such as:

``` text
CREATED
PAYMENT_PENDING
INVENTORY_RESERVED
COMPLETED
FAILED
```

## Order Details / Lifecycle

Example:

``` text
20:41:02  ORDER_CREATED
20:41:03  PAYMENT_PROCESSING
20:41:04  PAYMENT_COMPLETED
20:41:04  INVENTORY_RESERVING
20:41:05  INVENTORY_RESERVED
20:41:05  ORDER_COMPLETED
```

Goals:

-   [ ] Make eventual consistency visible
-   [ ] Show order state transitions
-   [ ] Surface correlation/order IDs
-   [ ] Make it possible to move from UI → logs/traces
-   [ ] Handle asynchronous status updates cleanly
-   [ ] Do not expose internal services directly simply for frontend
    convenience

------------------------------------------------------------------------

# 13. Phase 5 --- CI/CD & Reproducible Deployment

Also during January.

Pipeline concept:

``` text
Git Push / Pull Request
          │
          ▼
     CI Pipeline
          │
          ├── compile
          ├── unit tests
          ├── integration tests
          ├── mvn verify
          ├── package
          ├── Docker image build
          └── quality/security checks
```

Tasks:

-   [ ] Automated Maven build
-   [ ] Unit tests
-   [ ] Integration tests
-   [ ] Docker image build
-   [ ] Dependency/security checks
-   [ ] Meaningful quality gates

## Local Deployment Reproducibility

-   [ ] Document infrastructure startup
-   [ ] Document platform startup
-   [ ] Document monitoring startup
-   [ ] Document services startup
-   [ ] Eliminate undocumented manual fixes
-   [ ] Health checks
-   [ ] Environment-specific configuration
-   [ ] Externalised secrets
-   [ ] Document networks
-   [ ] Document volumes
-   [ ] Recovery/troubleshooting guide

Goal:

A clean repository checkout should be capable of reaching a working
environment through documented commands without hidden manual repairs.

------------------------------------------------------------------------

# 14. Phase 6 --- Controlled Cloud Deployment

**Target:** 1--14 February 2027

Only begin if the previous readiness gates are sufficiently healthy.

The goal is **not Kubernetes**.

The goal is to learn what changes when the application no longer runs on
the developer laptop.

Possible first architecture:

``` text
                 Internet
                    │
                    ▼
             Load Balancer
                    │
                    ▼
                  EC2
                    │
             Docker Services
                    │
           ┌────────┴────────┐
           ▼                 ▼
          RDS              Redis
```

Exact architecture should be decided at that time based on project
requirements and cost.

Tasks:

-   [ ] Networking
-   [ ] Security groups
-   [ ] Public/private boundaries
-   [ ] Secrets
-   [ ] Environment configuration
-   [ ] Persistence
-   [ ] Backups
-   [ ] Health checks
-   [ ] Deployment procedure
-   [ ] Restart procedure
-   [ ] Rollback procedure
-   [ ] Logs
-   [ ] Metrics/traces where practical
-   [ ] Cost awareness

Only afterwards evaluate whether ECS, managed Kafka, Kubernetes or other
infrastructure solves a genuine next problem.

------------------------------------------------------------------------

# 15. Phase 7 --- Production Hardening & Java Performance

**Target:** 15--28 February 2027

This final phase is deliberately focused on becoming a stronger
**Java/backend engineer**, rather than simply increasing the number of
technologies in the project.

------------------------------------------------------------------------

## 15.1 Load & Capacity Testing

Choose a realistic workload, for example concurrent order creation.

Before testing, define expectations.

Measure:

-   Throughput
-   Error rate
-   p50 latency
-   p95 latency
-   p99 latency
-   Kafka consumer lag
-   DB pool usage
-   CPU
-   Memory
-   GC
-   Redis behaviour

Tasks:

-   [ ] Establish baseline
-   [ ] Find first real bottleneck
-   [ ] Prove bottleneck with evidence
-   [ ] Make one justified optimisation
-   [ ] Repeat test
-   [ ] Compare before/after

Do **not** optimise toward arbitrary claims such as "support one million
users" without defining workload, concurrency and infrastructure.

------------------------------------------------------------------------

# 16. SQL & Connection-Pool Investigation

-   [ ] Identify important/slow queries
-   [ ] Inspect indexes
-   [ ] Read execution plans
-   [ ] Review transaction length
-   [ ] Identify where DB connections are held
-   [ ] Simulate connection-pool exhaustion
-   [ ] Understand queueing/timeouts
-   [ ] Avoid blindly increasing pool size
-   [ ] Test pessimistic locking under concurrency
-   [ ] Compare pessimistic vs optimistic locking trade-offs

------------------------------------------------------------------------

# 17. JVM & Java Runtime Investigation

Use the application as a JVM laboratory.

-   [ ] Observe heap behaviour
-   [ ] Observe GC under load
-   [ ] Understand HTTP thread usage
-   [ ] Understand Kafka consumer threads
-   [ ] Understand executor pools
-   [ ] Understand DB connection pools
-   [ ] Capture a thread dump during stress
-   [ ] Read the thread dump
-   [ ] Use Java Flight Recorder/profiling where practical
-   [ ] Investigate allocation/CPU hotspots
-   [ ] Relate observed behaviour to Java concurrency and memory
    concepts

Topics to strengthen:

-   Heap
-   Stack
-   GC
-   Allocation
-   Threading
-   Blocking
-   Synchronization
-   Executors
-   Virtual threads where relevant
-   Backpressure
-   Connection pools
-   CPU vs I/O-bound work

------------------------------------------------------------------------

# 18. Resilience Game Days

Deliberately break the system.

## Scenario A --- Consumer Failure

-   [ ] Kill a Kafka consumer during traffic
-   [ ] Observe rebalancing
-   [ ] Observe lag
-   [ ] Observe retry/recovery

## Scenario B --- Database Slowness

-   [ ] Introduce DB latency
-   [ ] Observe connection pool
-   [ ] Observe request latency
-   [ ] Observe retries/circuit behaviour

## Scenario C --- Redis Failure

-   [ ] Stop Redis
-   [ ] Verify correctness
-   [ ] Observe degraded performance
-   [ ] Verify recovery

## Scenario D --- Kafka Failure

-   [ ] Interrupt broker availability
-   [ ] Observe producer behaviour
-   [ ] Observe consumer behaviour
-   [ ] Verify Outbox/recovery expectations

For each scenario decide whether the correct response is:

``` text
Retry
Fail fast
Circuit break
Fallback
Compensate
Queue
DLT
Operator intervention
```

------------------------------------------------------------------------

# 19. Observability --- Continuous Track

Observability runs throughout all phases.

A request such as:

``` text
POST /api/orders
```

should eventually be traceable through:

``` text
Gateway
   │
   ▼
Order Service
   │
   ▼
Kafka
   │
   ├── Payment Service
   │       │
   │       └── MySQL
   │
   └── Inventory Service
           │
           ├── MySQL
           └── Redis
```

Metrics worth making useful:

-   [ ] Request rate
-   [ ] Error rate
-   [ ] p95/p99 latency
-   [ ] Kafka consumer lag
-   [ ] Kafka processing failures
-   [ ] JVM heap
-   [ ] GC
-   [ ] Threads
-   [ ] DB active connections
-   [ ] DB pool exhaustion
-   [ ] Redis hit/miss ratio
-   [ ] Circuit breaker state
-   [ ] Order success/failure counts

Operational exercise:

> Introduce a failure → identify symptoms from telemetry → find root
> cause → implement fix → verify improvement through telemetry.

------------------------------------------------------------------------

# 20. Architecture Decision Records

During the project, write short ADRs for important choices.

Potential ADRs:

-   [ ] Why Kafka?
-   [ ] Why database-per-service?
-   [ ] Why asynchronous Payment/Inventory workflows?
-   [ ] Why Transactional Outbox?
-   [ ] Why idempotent consumers?
-   [ ] Why Redis cache-aside?
-   [ ] Why MySQL remains authoritative for stock?
-   [ ] Why API Gateway is the public boundary?
-   [ ] Security architecture choice
-   [ ] Local Docker architecture
-   [ ] Cloud deployment choice
-   [ ] Why Kubernetes was or was not introduced

An ADR should generally record:

``` text
Context
Decision
Alternatives
Consequences
Trade-offs
```

------------------------------------------------------------------------

# 21. How We Will Work Through Each Topic

Use the same learning workflow throughout the roadmap.

## 1. You Propose

Explain what you think the design should be and why.

## 2. Challenge

Stress the proposal with:

-   concurrency
-   outages
-   duplicates
-   latency
-   scaling
-   partial failure
-   operational recovery

## 3. Correct / Extend

Fill gaps in:

-   Java
-   Spring
-   Kafka
-   SQL
-   Redis
-   security
-   JVM
-   distributed systems

Also distinguish what would normally be expected from:

-   mid-level reasoning
-   strong-mid reasoning
-   senior-level reasoning

## 4. Implement

Apply the decision to the real project.

## 5. Break It

Run at least one realistic failure scenario.

## 6. Observe It

Use logs, traces and metrics.

## 7. Defend It

Answer interview-style questions and trade-offs.

## 8. Document It

Update README/architecture notes before moving forward.

------------------------------------------------------------------------

# 22. Scope-Control Rules

Do **not** add Kubernetes merely because it appears in job descriptions.

Do **not** add another microservice unless a real bounded responsibility
exists.

Do **not** expose internal REST APIs simply to make the frontend easier.

Do **not** use Redis as authoritative stock state without an explicit
consistency design.

Do **not** add retries around non-idempotent side effects until
duplicate execution is controlled.

Do **not** call a feature complete until its failure behaviour is
understood.

Do **not** optimise performance before measuring it.

Do **not** introduce a distributed lock merely because Redis supports
one.

Do **not** introduce another database simply to demonstrate polyglot
persistence.

Do **not** confuse architectural complexity with architectural maturity.

------------------------------------------------------------------------

# 23. Monthly Checkpoints

## 31 October 2026

Expected state:

-   Architecture audit substantially complete
-   Current architecture understood
-   Major FIX/IMPROVE items prioritised
-   Backend reliability work started

## 30 November 2026

Expected state:

-   Kafka reliability substantially improved
-   Outbox/idempotency/DLT strategy understood
-   Redis consistency strategy established
-   Integration/failure testing established

## 31 December 2026

Expected state:

-   Security architecture implemented
-   Authentication/authorization tested
-   Trust boundaries documented

## 31 January 2027

Expected state:

-   Small frontend demonstrates the distributed workflow
-   CI/CD pipeline exists
-   Local deployment is reproducible
-   Project can be demonstrated end-to-end

## 28 February 2027

Expected state:

-   Controlled cloud deployment completed if readiness allowed
-   Load/performance investigation completed
-   SQL/JVM behaviour investigated
-   Resilience game days performed
-   Final architecture documentation completed
-   Project is ready to be used as a substantial Java backend interview
    case study

------------------------------------------------------------------------

# 24. Starting Point --- 1 October 2026

**Do not start by implementing a new feature.**

Begin with Architecture Audit 0.1.

First inputs:

-   [ ] Full current repository tree
-   [ ] Root `pom.xml`
-   [ ] `shared-events` structure
-   [ ] `shared-events/pom.xml`
-   [ ] Relevant root configuration

Then:

1.  Verify the module architecture.
2.  Verify Maven/dependency management.
3.  Review `shared-events`.
4.  Record findings.
5.  Move to Infrastructure.
6.  Continue layer by layer.
7.  Review services individually.
8.  Trace the complete distributed workflow.
9.  Update this roadmap from evidence.

------------------------------------------------------------------------

# 25. Finish Line --- 28 February 2027

The target is **not**:

> "I used a lot of enterprise technologies."

The target is:

> "I designed and evolved a distributed Java system, understand why its
> major components exist, can explain its consistency and failure
> behaviour, can test and observe it, can investigate performance
> problems, and can defend the architectural trade-offs."

By the end, the project should demonstrate practical understanding of:

``` text
Java / JVM
Spring Boot
Spring Security
REST
Kafka
Event-driven architecture
Distributed transactions
Transactional Outbox
Idempotency
Retries / DLT
MySQL
Transactions
Concurrency / locking
Redis
Caching
Docker
Service discovery
API Gateway
Observability
Integration testing
CI/CD
Cloud fundamentals
Performance analysis
Failure engineering
System design
```

But the most important result is not the list above.

It is being able to answer:

> **Why is each piece there, what problem does it solve, what happens
> when it fails, and what trade-off did we accept by using it?**

------------------------------------------------------------------------

## Progress

**Roadmap started:** 1 October 2026\
**Target completion:** 28 February 2027

Current phase:

``` text
PHASE 0 — ARCHITECTURE AUDIT
Status: NOT STARTED
```

------------------------------------------------------------------------

> **Project rule:** Finish fewer things, but finish them deeply enough
> to explain, test, break, observe and operate them.
