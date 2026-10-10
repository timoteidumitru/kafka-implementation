# Cross-Service Architecture & Deployment Integration Audit

**Project:** `kafka-microservices` · Java 21 · Spring Boot 3.3.3 · Kafka · MySQL · Redis · Eureka · Docker Compose  
**Date:** 9 October 2026  
**Status:** Evidence-based architecture review, **not** implementation or runtime certification  
**Scope:** Six stages: business workflow; shared event contracts; distributed reliability; Gateway/Eureka integration; four-layer deployment; test/verification coverage. No code was modified.

## Executive summary

The project has a coherent learning-oriented foundation: a multi-module Maven reactor, Order-owned HTTP API, asynchronous Kafka-driven Payment/Inventory/Notification services, service-owned database schemas, consistent order-ID aggregate keys, immutable event records, a useful correlation-ID mechanism, pessimistically locked inventory reservations, explicit Gateway routes, Eureka discovery, Redis rate-limiter intent, and four independently managed Docker Compose layers. **Retain these choices.** The main Phase 1 objective is not to add more infrastructure; it is to establish **correctness and recoverability under duplicate delivery, crashes, partial failure and deployment restarts**.

**Top priorities:** fix Kafka event serialization and actual topic wiring; correct idempotency transaction boundaries; implement a durable database-to-Kafka publication strategy; specify and implement the Saga state machine and compensation; remove unintended Gateway exposure and broken rate-limiter references; protect MySQL data; and introduce tests early, in parallel with every reliability fix. The user confirmed **no automated tests**, **no transactional outbox**, **no DLT replay**, **no compensation implementation**, and **no persisted Payment aggregate**.

**Evidence discipline:** `C` = directly confirmed in supplied files or explicit user confirmation; `R` = supported risk, exact runtime manifestation untested; `V` = verify with missing code/runtime tests; `P` = gap specifically before external/public deployment. Do not treat a successfully compiling Maven reactor as proof of behavioral correctness. Findings below consolidate the six-stage review and cross-reference earlier service/infrastructure audits rather than re-counting them as newly discovered defects.

## 1. System map and architectural decisions

```text
External HTTP client
    |
    v
API Gateway :8080 --(Eureka lb://order-service)--> Order Service :8081 / orders_db
    |                                                | publishes OrderCreated
    |                                                v
    +-- Redis rate limiting                        Kafka topics
                                                     |
                   +---------------------------------+-------------------------+
                   |                                 |                         |
             Payment Service                   Inventory Service         Notification Service
             payments_db                       inventory_db              (notification storage)
             OrderCreated ->                   PaymentCompleted ->        currently legacy-topic
             PaymentCompleted/Failed           InventoryReserved/Failed   subscription mismatch
                   |                                 |
                   +------------- Kafka -------------+----> Order completes/cancels

Infrastructure Compose: Kafka, ZooKeeper, MySQL, Redis, topic initialization
Platform Compose: Eureka, Gateway, Kafka Exporter, Zipkin
Monitoring Compose: Prometheus, Alertmanager, Grafana
Services Compose: Order, Payment, Inventory, Notification
All use external Docker network: kafka-network
```

**KEEP:** Kafka choreography as the initial workflow style; Order as HTTP entry and order-status owner; per-service data ownership; `DomainEvent` with immutable records; unique `eventId`, preserved `correlationId`, `occurredAt`, producer identity; `orderId` as event aggregate/key for order workflow; inventory's transactional pessimistic row lock and version field (test their interaction); explicit Gateway `lb://` routes; Maven root reactor; four logical Compose layers; Prometheus/Grafana persistence; Actuator and tracing. Do **not** introduce Kubernetes, service mesh, separate Saga orchestrator, Debezium, or a full hexagonal rewrite without an evidenced need.

**Decisions pending Phase 1 ADRs:** (A) choreography vs orchestration (recommend choreography initially); (B) reserve inventory before payment vs authorize payment first (recommend explicitly comparing failure/compensation costs); (C) payment authorization/capture/refund semantics; (D) outbox publisher implementation; (E) event discriminator and schema evolution; (F) internal vs host-published ports and local/prod profiles. Document decisions and tradeoffs, not only code.

## 2. Consolidated findings register — Stages 1–6

Severity: **Critical** = can lose/duplicate business effects or leave irreconcilable state; **High** = significant correctness, availability, isolation or test gap; **Medium** = maintainability/operability; **Later** = conditional future architecture. `FIX`, `IMPROVE`, `VERIFY`, `KEEP`, `LATER` are proposed dispositions.

### Stage 1 — Business workflow

| ID | Severity/evidence | Finding and cross-service consequence | Phase 1 action |
|---|---|---|---|
| CSA-01 | **Critical · C/R** | Order controller saves Order then publishes Kafka event independently: database/Kafka dual-write gap. Also applies to other service transitions. | **FIX:** atomic DB state + outbox row, idempotent publication; reference INF producer delivery findings. |
| CSA-02 | **High · C** | Order `markCompleted()`/`markCancelled()` have no guarded transition or `@Version`; concurrent/stale events can overwrite final status. | **FIX:** explicit state machine, optimistic locking or conditional SQL, tests. |
| CSA-03 | **High · C** | Payment has only `ProcessedEvent`, not a durable payment lifecycle or provider reference. | **FIX:** minimal Payment aggregate, durable operation identity and outcome. |
| CSA-04 | **High · C** | Inventory decrements product stock but has no order-linked reservation record; cannot safely release/reconcile a particular order's allocation. | **FIX:** persisted reservation and release semantics, unique business key. |
| CSA-05 | **Critical · C** | Payment can succeed before inventory failure; Order cancellation does not reverse payment. | **FIX:** Saga compensation or authorization/void strategy. |
| CSA-06 | Medium · C | POST returns raw UUID despite asynchronous fulfillment; no demonstrated status endpoint. | **IMPROVE:** `202 Accepted` + status URL/representation; GET order status. |
| CSA-07 | Medium · C | `CreateOrderRequest` has no constraints; controller lacks `@Valid`; invalid quantity/price can persist before downstream rejection. | **FIX:** Bean Validation and domain invariants. |
| CSA-08 | **KEEP/VERIFY · C** | Inventory uses `@Transactional`, `@Lock(PESSIMISTIC_WRITE)` and `@Version`; positive concurrency foundation, but no actual race test. | **VERIFY:** concurrent reservations, unique `productId`, rollback; document whether both locking modes needed. |
| CSA-09 | **High · C** | Client supplies `price` used in simulated payment; untrusted clients can influence amount. | **FIX before real payments:** server-authoritative pricing, currency, price snapshot. |

### Stage 2 — Event contracts

| ID | Severity/evidence | Finding and consequence | Phase 1 action |
|---|---|---|---|
| CSA-10 | High · C for reviewed flow | `PaymentRequestedEvent` and `InventoryReserveRequestedEvent` are defined but current choreography reacts directly to `OrderCreatedEvent`/`PaymentCompletedEvent`. | **DECIDE:** keep choreography and retire unused contracts, or implement commands deliberately; repository-wide usage search first. |
| CSA-11 | Medium · C | Requested-events and past-tense facts blur command vs event intent. | **IMPROVE:** documented naming and ownership convention. |
| CSA-12 | High · C | `PaymentCompletedEvent` carries product/quantity but no amount, currency, payment ID or durable provider reference. | **FIX:** align contract to actual persisted payment outcome; decide how Inventory receives product/quantity. |
| CSA-13 | High · C | Failure events expose free-text reason only; business rejection and technical failure are not distinguishable by stable codes. | **FIX:** typed failure codes; transient infrastructure errors generally retry rather than publish terminal domain failure. |
| CSA-14 | Medium · C | Correlation IDs propagate correctly, but metadata lacks `causationId`. | **IMPROVE/LATER:** add only if useful for trace/replay investigation. |
| CSA-15 | Medium · C | Only `OrderCreatedEvent` defines a VERSION constant; other events rely on caller-supplied metadata version. | **FIX:** consistent event contract versions, supported-version tests, evolution policy. |
| CSA-16 | **High · R/V** | Disabled type headers + `JsonDeserializer<Object>` + multiple record types per topic: polymorphic reconstruction not proven by `getEventType()`. | **VERIFY/FIX:** real Kafka producer→consumer contract test; explicit trusted discriminator/envelope strategy. |
| CSA-17 | Medium · V | Request events have no usage in supplied listener/publisher flow; full-repo references not checked. | **VERIFY:** search all usages before removal. |

**Metadata invariant:** `EventMetadata.create()` generates new event/correlation IDs; `EventMetadataFactory.next(previous, source, version)` generates a new event ID and preserves correlation. `getAggregateId()` returns `orderId` for all supplied events. These are **KEEP** findings. Ordering by key applies **within a topic partition**, not across independent topics.

### Stage 3 — Distributed reliability

| ID | Severity/evidence | Finding and consequence | Phase 1 action |
|---|---|---|---|
| CSA-18 | **Critical · C** | Order/Payment `alreadyProcessed()` commits a processed marker before listener business work. Inventory also commits its insert-first marker before work. A crash/exception can cause Kafka redelivery to skip uncompleted business processing. | **FIX:** claim event and mutate domain state within one DB transaction; include outgoing outbox row in same transaction. |
| CSA-19 | High · C/R | Order/Payment `existsById`→`save` has a race; Inventory's `saveAndFlush`+catch is stronger for unique claims but caught DB integrity exceptions can poison transaction and may reflect other constraints. | **FIX:** atomic unique claim/upsert with carefully defined duplicate handling and concurrency tests. |
| CSA-20 | **Critical before real charging · C** | No persisted Payment/provider idempotency or reconciliation for ambiguous external charge outcomes. | **FIX:** stable payment operation/provider key, persisted state, reconciliation of unknown outcomes. |
| CSA-21 | **Critical · C** | No transactional outbox; DB commits and Kafka sends can diverge at every service boundary. | **FIX:** outbox in each publishing service or equivalent proven guarantee; accept at-least-once publication and deduplicate downstream. |
| CSA-22 | High · C | DLT error handlers exist but no DLT inspection/replay workflow; some DLT topics unprovisioned. | **FIX:** provision destinations, classify failures, controlled replay, audit trail and alerting. Cross-ref Audit 08. |
| CSA-23 | **Critical · C** | No refund/void/release compensation implementation or compensating business-state model. | **FIX:** explicit Saga failure transitions and idempotent compensating actions. |
| CSA-24 | **Critical · R** | Resilience4j fallback can return normally and prevent Spring Kafka `DefaultErrorHandler` from seeing processing failure. | **VERIFY/FIX:** one owner of listener retries; propagate errors appropriately; targeted dependency circuit breakers. Cross-ref Audit 08. |

**Critical transactional rule:** `@Transactional` on `IdempotencyGuard.alreadyProcessed()` is **not** enough when the caller executes business logic after that method returns. For database-only handlers, one transaction must cover unique event claim + domain mutation + outbox insert. For external payment calls, a database transaction cannot atomically commit a provider-side effect; provider idempotency and reconciliation are separate necessities. The outbox does **not** guarantee exactly-once end-to-end delivery.

### Stage 4 — Gateway and discovery

| ID | Severity/evidence | Finding and consequence | Phase 1 action |
|---|---|---|---|
| CSA-25 | High · C | Gateway references `#{@ipKeyResolver}`, but user confirms no `KeyResolver` bean; route initialization/rate limiting may fail. | **FIX:** define/test resolver, trusted proxy behavior and `429` response. |
| CSA-26 | High · C/R | Discovery locator enabled alongside explicit routes; may expose service-ID routes beyond intended Order API. | **FIX:** disable auto-generated discovery routes, retain `lb://order-service`. |
| CSA-27 | Medium · C | Gateway base YAML forces `spring.profiles.active=docker`; runtime also sets profile, but local IntelliJ runs inherit Docker defaults. | **IMPROVE:** select profile at launch/deployment. |
| CSA-28 | Medium · C/V | Base YAML hardcodes Docker hostnames; Gateway uses `spring.redis` instead of expected Boot 3.3 `spring.data.redis` namespace. | **FIX/VERIFY:** environment config and Redis binding test. |
| CSA-29 | Medium · C for supplied code | Gateway GET `/api/orders/**` route exists, but reviewed OrderController only exposes POST. | **VERIFY/FIX:** implement GET status or remove route. |
| CSA-30 | **High before public deployment · P** | Identity/JWT/authorization intentionally deferred; userId comes from untrusted request. | **PLANNED:** authn/authz workstream; no assumption that a new Identity microservice is mandatory. |
| CSA-31 | Medium · C | Services publish host ports `8081`–`8084`, allowing host-side bypass of Gateway controls. | **FIX:** restrict publishing; document local debug exceptions. |
| CSA-32 | Medium · V | Eureka registration and `/actuator/health` do not prove DB/Kafka/business readiness. | **VERIFY:** dependency restarts, readiness and routing behavior. |
| CSA-33 | Medium · C/V | Gateway `management.zipkin.endpoint` differs from Eureka's `management.zipkin.tracing.endpoint`; actual tracing binding unverified. | **FIX/VERIFY:** standardize and test trace export; cross-ref Observability audit. |

### Stage 5 — Four-layer deployment

| ID | Severity/evidence | Finding and consequence | Phase 1 action |
|---|---|---|---|
| CSA-34 | High · C | Separate Compose projects share network but cannot use ordinary `depends_on` to enforce cross-project readiness; services have no infrastructure readiness gate. | **FIX:** scripted, verified startup + application-level reconnection. |
| CSA-35 | High · C | Business services have no Compose healthchecks or restart policies. | **FIX:** healthchecks, appropriate restart policies, verify crash recovery. |
| CSA-36 | Medium · C | Dockerfiles `COPY target/<service>-1.0.0.jar` require Maven artifacts built in advance; stale JAR packaging possible. | **FIX:** root `./mvnw`/`mvnw.cmd clean verify` before image build, script and CI; consider multi-stage builds later. |
| CSA-37 | Medium · C | Third-party and locally built images use mutable `latest` tags. | **IMPROVE:** pin versions/digests and version application images. |
| CSA-38 | Medium · C | Zipkin and Kafka Exporter reside in Platform rather than Monitoring layer. | **IMPROVE:** consolidate as Observability layer, cross-ref Audit 08. |
| CSA-39 | Medium · C/V | Repeated topic env vars across services; names may not bind to `app.*` property names actually consumed. | **FIX:** one explicit topic binding convention and runtime verification. |
| CSA-40 | Medium · C | Alertmanager has restart policy; most platform/monitoring/business containers do not. | **FIX:** document and test recovery policies. |
| CSA-41 | Medium · V | Grafana's short-form `depends_on` starts Prometheus but does not wait for health; recovery untested. | **VERIFY:** telemetry and dashboard reconnect after restarts. |
| CSA-42 | **Critical to persistent data · C** | Audit 08 identified missing MySQL `/var/lib/mysql` volume; recreating DB container can lose all service data. | **FIX FIRST:** persistent volume and backup/restore exercise; cross-ref INF-01. |

**Other cross-references:** hardcoded `admin/admin`; broad port publishing; `ddl-auto=update`; legacy/possibly ineffective Prometheus export property; Gateway's `EUREKA_SERVER` env var may not bind to Eureka client settings; `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` needs effective-property verification; `notification.events.v1` and DLT naming need consistency; full JDK runtime is larger than necessary; Kafka Exporter restart/connectivity needs testing. See audits 05, 06, 07 and 08 for service-level detail.

### Stage 6 — Verification and testing

The user explicitly confirmed **no implemented automated tests**, no supplied Postman/CLI smoke tests, and no demonstrated failure-injection suite. Do not claim a test suite exists or that Maven `verify` executed meaningful assertions.

| ID | Severity/evidence | Finding | Phase 1 action |
|---|---|---|---|
| CSA-43 | High · C | No unit/service-level regression tests. | **FIX:** JUnit 5, AssertJ, Mockito, Spring Boot Test; start with domain invariants. |
| CSA-44 | **Critical for validating reliability · C** | No real MySQL/Kafka integration tests for transactions, duplicates, serialization and retries. | **FIX:** Testcontainers MySQL + Kafka; concurrency and rollback tests. |
| CSA-45 | High · C | No automated complete Saga happy/failure-path tests. | **FIX:** multi-service E2E with Awaitility and state assertions. |
| CSA-46 | High · C | No crash, duplicate-delivery, Kafka outage, DLT or compensation recovery tests. | **FIX:** targeted fault injection, bounded retries, replay/reconciliation checks. |
| CSA-47 | Medium · C | No demonstrated deployment smoke-test script. | **FIX:** PowerShell/CLI health + create/order-status + telemetry checks. |
| CSA-48 | High · C | Build passes but no behavioral regression gate. | **FIX:** Maven Surefire/Failsafe strategy and CI verification, with integration tests enabled in agreed profiles. |

## 3. Event ownership and consistency matrix

| Published fact | Producer | Consumer in reviewed workflow | Topic | Contract concern |
|---|---|---|---|---|
| `OrderCreatedEvent` | Order | Payment | `order.events.v1` | Client-provided price; atomic publish absent |
| `PaymentCompletedEvent` | Payment | Inventory | `payment.events.v1` | Payment result lacks amount/currency/payment identity; provider result not persisted |
| `PaymentFailedEvent` | Payment | Order | `payment.events.v1` | Unstructured failure reason; technical vs business error unclear |
| `InventoryReservedEvent` | Inventory | Order | `inventory.events.v1` | No durable order-linked reservation |
| `InventoryReservationFailedEvent` | Inventory | Order | `inventory.events.v1` | No payment compensation |
| `OrderCompletedEvent` | Order | Notification intended, actual subscription mismatch | `order.events.v1` | Notification currently uses legacy topic names |
| `OrderFailedEvent` | Order | Notification intended, actual subscription mismatch | `order.events.v1` | Terminal state/compensation not reconciled |
| `PaymentRequestedEvent` | Not shown | Not shown | Undetermined | Command-like unused contract |
| `InventoryReserveRequestedEvent` | Not shown | Not shown | Undetermined | Command-like unused contract |

**Note:** Producers/consumers are based on classes reviewed across audits, not a runtime Kafka trace. All supplied event types return `orderId` as `aggregateId`. Topic creation in Audit 08 provisions `order.events.v1.DLT` but not Payment/Inventory `.DLT`; Notification's legacy topics and `.dlq` are not aligned with the declared topic set. Verify concrete class dispatch before declaring the flow operational.

## 4. Failure scenario matrix and expected guarantees

| Scenario | Current risk | Required target behavior | Test layer |
|---|---|---|---|
| Same event delivered twice sequentially | Marker may skip incomplete prior operation | Exactly one committed business transition for a stable event ID | MySQL/Kafka integration |
| Same event delivered to concurrent workers | `existsById` race | Unique claim; one winner, deterministic duplicate handling | Concurrency integration |
| Crash after marker commit, before state update | Lost business effect on redelivery | Marker rolls back with business work | Transaction integration |
| DB commits, Kafka send fails | Downstream never sees transition | Durable outbox retries; event ID stable | Outbox integration |
| Kafka send succeeds, publisher crashes before outbox ack | Duplicate event after retry | Downstream deduplication; eventual completion | Outbox + consumer integration |
| Payment succeeds, inventory insufficient | Order cancelled but charge retained | Void/refund or revised sequence; persisted compensation | Saga E2E |
| Provider timeout after possible charge | Uncertain payment outcome; duplicate-charge risk | Stable provider key, reconciliation before retry | Payment adapter/fake provider |
| Inventory receives two reservations for limited stock | Potential oversell if locking fails | Lock serializes; one succeeds, one fails | MySQL concurrency |
| Kafka unavailable during startup | Service startup/recovery inconsistent | Bounded startup checks, reconnect or clear failure | Deployment smoke/fault |
| Redis unavailable | Gateway limiter may reject/error | Document fail-open/fail-closed policy; visible behavior | Gateway integration |
| Eureka unavailable or stale instance | Route failures despite app health | Defined service discovery failure response/recovery | Gateway integration |
| Poison event enters DLT | No operational replay | Inspect, classify, replay safely with audit | Kafka integration/ops |
| MySQL container recreated | Data loss without volume | Durable volume and verified restore | Deployment recovery |
| Notification consumes old topic | No notifications for current events | Aligned topic and contract tests | Kafka E2E |

## 5. Phase 1 test strategy — start immediately, not at the end

**Tooling:** JUnit Jupiter (JUnit 5), AssertJ, Mockito, `spring-boot-starter-test`, Spring Kafka test utilities as appropriate, Testcontainers for MySQL/Kafka (and Redis if needed), Awaitility for asynchronous assertions, Spring MVC MockMvc for controller tests, WebTestClient for reactive Gateway tests, JaCoCo for visibility, and optional Postman/Newman for external smoke workflows. Match versions through the Spring Boot dependency management and verify Docker Desktop/Testcontainers support on Windows. Do not adopt Pact, Cucumber, full chaos tooling or external cloud test platforms until a real requirement exists.

**Milestone T1 — Build/test foundation**
- Audit each child POM for `spring-boot-starter-test`, JUnit engine and Maven Surefire/Failsafe configuration; choose `*Test` for fast tests and `*IT` for integration tests.
- Confirm root `mvnw.cmd clean verify` runs unit tests and the intended integration-test profile (do not silently skip integration tests).
- Add a failing-example test to prove CI/build gates actually fail; configure JaCoCo without arbitrary early percentage gates.
- **Done when:** a deliberately broken assertion fails the Maven reactor and CI; test reports are visible.

**Milestone T2 — Domain/unit tests**
- `Order`: CREATED→COMPLETED/CANCELLED, invalid/repeated/conflicting transitions.
- `InventoryItem`: zero/negative quantities, insufficient stock, correct subtraction, no negative stock.
- `PaymentService`: invalid amounts, stable amount calculation, fake-provider outcome mapping; after refactor, Payment state transitions.
- `CreateOrderRequest`/controller: null identifiers, nonpositive quantity/price, unauthorized price assumptions, HTTP status contract.
- **Done when:** business invariants fail fast without Kafka or a database.

**Milestone T3 — MySQL transaction tests**
- Testcontainers MySQL with actual JPA mappings and migrations; verify `@Lock(PESSIMISTIC_WRITE)` with parallel transactions.
- Prove unique processed-event claim under concurrent delivery and rollback on domain failure.
- Test optimistic version conflict/conditional Order state update; unique reservation per business operation.
- **Done when:** failed processing leaves neither a completed marker nor a partial state change.

**Milestone T4 — Kafka and contract tests**
- Produce and consume each concrete event type using the **same serializer/deserializer configuration as production**.
- Assert topic names, aggregate keys, correlation/event IDs, schema versions, retries, DLT destination and replay.
- After implementing outbox, simulate send failures and duplicate publication.
- **Done when:** all cross-service events deserialize to expected concrete types and recover correctly after tested failure modes.

**Milestone T5 — End-to-end Saga tests**
- Bring up service dependencies with Testcontainers or a dedicated Compose test environment; avoid shared mutable developer DBs.
- Verify success, payment business rejection, insufficient stock, compensation, duplicate events and terminal Order status via API.
- Use Awaitility with bounded timeouts instead of `Thread.sleep()` assumptions.
- **Done when:** each scenario converges to a valid state with no duplicate charge/reservation and no unaccounted compensation.

**Milestone T6 — Deployment and resilience tests**
- PowerShell smoke script: Maven build → Compose startup → health/readiness → topic existence → Gateway POST/GET → eventual order outcome → metrics/traces.
- Restart Kafka, MySQL, Redis, Eureka and one business service in a disposable environment; verify recovery and visibility.
- Test `429`, unauthorized route exposure, missing service routing, DLT inspection/replay and data persistence across **container recreation**.
- **Done when:** a new developer can reproduce startup and smoke checks from documented commands, and expected failure behavior is recorded.

**CI recommendation:** run fast unit tests on every push/PR; run DB/Kafka integration tests on PRs where feasible; run multi-service E2E/smoke on main/nightly or before release. Prefer reliable smaller suites over a single slow flaky all-in-one test. `mvn verify` must have a documented definition of what it covers.

## 6. Integrated Phase 1 sequence and dependencies

| Wave | Work | Why this order | Exit criteria |
|---|---|---|---|
| **0 — Baseline safety** | T1 test framework; MySQL persistence/backup; documented clean startup; fix missing Gateway resolver so app starts | Preserve data and establish regression feedback before risky refactors | Repeatable build, healthy baseline, safe test data |
| **1 — Make event transport truthful** | Fix Kafka deserialization/type discriminator, topic/DLT provisioning, Notification subscriptions, producer send-error visibility, listener exception propagation; T4 contract tests | Cannot validate Saga if events are misrouted or swallowed | Every event type traverses intended path; failure reaches configured handler |
| **2 — Define business invariants** | Saga ADR, guarded Order transitions, request validation/server pricing, Payment aggregate, order-linked InventoryReservation; T2/T3 | Stable business semantics before transactional rework | Valid transitions, business identifiers, durable outcomes |
| **3 — Transactional reliability** | Atomic processed-event claim + domain mutation; transactional outbox per publishing service; duplicate/restart tests | Prevent lost effects and DB/Kafka divergence | Replay does not corrupt state; crash tests pass |
| **4 — Compensation and recovery** | Refund/void/release workflow, uncertain payment reconciliation, DLT inspection/replay; T5 | Recovery must be designed against persisted state and durable messaging | Every modeled failure reaches valid terminal/recoverable state |
| **5 — Boundary and deployment hardening** | Disable auto discovery routes, GET status, secure ports/profiles, healthchecks/restart, pinned images, scripted cross-layer readiness, telemetry; T6 | Operationalize proven business flow | New environment can build, launch, recover, and smoke-test |
| **6 — Planned expansion** | Identity/JWT and per-order authorization before public exposure; CI depth, cloud deployment, scaling | Not a prerequisite for local learning but mandatory before untrusted access | Security and production readiness criteria documented |

**Parallelism:** T2 accompanies business refactors; T3 accompanies transaction changes; T4 accompanies event changes; T5 accompanies Saga changes. Do **not** defer testing until Wave 5. Waves are dependency-based, not calendar estimates.

## 7. Concrete acceptance checklist

- [ ] Root Maven reactor executes and reports meaningful unit and integration tests.
- [ ] A created order can be retrieved through an explicit Gateway GET status endpoint.
- [ ] Invalid order requests fail before persistence or event emission.
- [ ] No client-supplied price can silently determine an actual charge.
- [ ] All supplied event types round-trip through real Kafka serializer/deserializer configuration.
- [ ] Topic constants, consumer subscriptions and provisioned topics/DLTs agree.
- [ ] Reprocessing the same event does not duplicate payment, inventory or Order state transitions.
- [ ] A failed transaction rolls back its processed-event claim and domain update together.
- [ ] Business state and outgoing event are durably committed together using outbox/equivalent.
- [ ] Outbox duplicate publication is tolerated by downstream consumers.
- [ ] Payment and inventory have durable operation/reservation identities and compensating outcomes.
- [ ] Business rejection is distinguished from transient technical failure.
- [ ] Kafka listener failures are not silently swallowed by Resilience4j fallbacks.
- [ ] DLT routing, inspection and controlled replay are demonstrated.
- [ ] Gateway has a working rate-limiter resolver and does not auto-expose unintended service routes.
- [ ] Internal service ports are restricted in the normal deployment profile.
- [ ] MySQL survives container recreation; backup/restore is verified.
- [ ] All four Compose layers have a reproducible, health-checked startup/recovery procedure.
- [ ] End-to-end tests cover success, insufficient inventory, payment failure, compensation and service restart.
- [ ] Trace/metric coverage makes failed business operations and stuck workflows visible.
- [ ] Authentication and authorization are in place **before** public/untrusted deployment.

## 8. Questions and evidence still needed for implementation (not blockers to Phase 0 completion)

1. Actual `EventType` enum and complete producer/listener methods: verify all event-type names, fallback behavior, exact Kafka serializers and keys.
2. Root/child Maven POMs and current `src/test` tree: select dependencies, Surefire/Failsafe profiles and test ownership.
3. All effective `application*.yml` and `KafkaConsumerConfig`/`KafkaProducerConfig`: establish property binding and JSON type dispatch in running applications.
4. Kafka `create-topics.sh`, MySQL init and Docker healthchecks: verify startup sequencing and persistence changes without risking data.
5. Existing README/PowerShell startup commands: capture the actual manual workflow before automating it.
6. Decide payment sequencing and authorization/capture/refund contract **before** choosing final compensation event payloads.
7. Confirm whether Notification has its own database configuration and how its storage is provisioned; don't infer from Compose omission alone.

## 9. Cross-reference to earlier audits

- **01 Order / 02 Payment / 03 Inventory:** domain transitions, payment simulation, inventory locking, listener idempotency and per-service implementation details.
- **04 Notification:** legacy Kafka subscriptions, notification processing and DLQ inconsistencies.
- **05 API Gateway:** route/filter configuration, rate limiting, security and external surface.
- **06 Eureka:** service registration and discovery configuration.
- **07 Observability:** trace export, metrics, Grafana/Prometheus and alerting.
- **08 Infrastructure:** Kafka/DLT provisioning, producer/consumer factories, Docker networking, MySQL persistence, credentials, startup readiness.

This report is an **integration overlay**: its findings explain how individually identified issues interact across service, messaging and deployment boundaries. Where an issue appears in an earlier audit, Phase 1 should track **one implementation task** with multiple report references, not duplicate the work.

## Final architectural judgment

The system is a credible event-driven microservice **learning platform**, not yet a verified fault-tolerant commerce workflow. The architecture's highest-value next investment is **behavioral proof**: automated tests, transactional idempotency, durable event publication, explicit business state/compensation, and repeatable recovery. Preserve the four-layer Compose design and the existing service separation; improve reliability before adding infrastructure complexity.
