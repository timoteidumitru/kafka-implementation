# Infrastructure & Kafka Integration Architecture Audit

**Project:** `kafka-microservices` · Spring Boot 3.3.3 · Java 21 · Kafka / MySQL / Redis / Docker Compose  
**Status:** Phase 0 evidence-based audit; **not** an implementation or runtime certification  
**Date:** 8 October 2026  
**Scope:** `setup/1.infrastructure/infra-compose.yml`, `kafka/create-topics.sh`, `mysql/init.sql`, `setup/4.services/services-compose.yml`, and producer, listener, producer/consumer configuration classes supplied for Order, Payment, Inventory, Notification. Observability and platform topology considered only where cross-layer ownership matters.

## Executive summary

The project has a useful four-layer local-development deployment, explicit Kafka topic provisioning, service-specific logical databases, keyed event publishing, Spring Kafka error-handler infrastructure for three business services, and named storage for Kafka and Redis. **Keep these foundations.** The most important work is not to add Kubernetes or a multi-broker cluster; it is to make existing configuration **consistent, secure enough for its environment, testable, and failure-aware**.

**Highest-priority confirmed issues:** (1) MySQL lacks a data volume; (2) Notification subscribes to obsolete topic names and sends failures to an unprovisioned DLQ; (3) Order and Inventory DLT destinations are unprovisioned; (4) Kafka producers do not observe asynchronous send completion; (5) some fallback methods swallow processing or publication errors; (6) custom Kafka factories can ignore `spring.kafka.*` settings; (7) service DB access uses one account with access to all databases; (8) serialization/type dispatch is inconsistent and needs an actual integration test. Some findings are *risks requiring verification*, not proven runtime failures.

**Audit rule:** Do not conflate local-development compromises with production defects. A single Kafka broker, one MySQL server, plaintext Docker-internal networking and static service discovery can be reasonable locally if limitations are documented and production requirements are separately defined.

## Evidence and confidence

- **Confirmed (C):** directly visible in supplied source/configuration.
- **Likely risk (R):** code path or configuration permits an undesirable outcome; runtime behavior depends on framework behavior and other files.
- **Verify (V):** cannot be concluded from supplied files; requires integration tests or missing classes.
- **Production gap (P):** acceptable locally but must change before a production deployment.

No broker logs, application startup traces, event model definitions, Docker runtime checks, full application profiles, or integration-test results were supplied. The pasted files are the evidence base. Do not label an issue as *runtime proven* unless tested.

## What to keep

| ID | Decision | Reason |
|---|---|---|
| KEEP-01 | Four independently managed Compose layers | Good separation for learning, debugging and incremental startup |
| KEEP-02 | Explicit Kafka topic provisioning with broker auto-create disabled | Makes topic topology intentional |
| KEEP-03 | Kafka init job waiting for broker readiness | Better than racing topic creation against broker startup |
| KEEP-04 | Pinned Confluent image versions | Reproducible compared with `latest` |
| KEEP-05 | Named Kafka, ZooKeeper and Redis volumes | Provides a persistence foundation; verify Redis persistence mode |
| KEEP-06 | Dedicated logical databases | Correct starting point for database-per-service |
| KEEP-07 | Kafka event keys derived from aggregate IDs | Good pattern **if** all relevant event aggregate IDs are the same order ID |
| KEEP-08 | `acks=all` in producers | Good durability setting within available replicas; not a substitute for replication |
| KEEP-09 | `ErrorHandlingDeserializer` and `DefaultErrorHandler` in Order, Payment, Inventory | Useful Spring Kafka error-handling building blocks |
| KEEP-10 | Three partitions and consumer concurrency three as a local experiment | Reasonable test bed, not a universal optimal value |
| KEEP-11 | External Docker network between independently launched Compose projects | Valid pattern with explicit lifecycle ownership |
| KEEP-12 | `SPRING_PROFILES_ACTIVE=docker` in application Compose | Enables environment-specific application configuration |

## Consolidated findings register

**Severity:** Critical = likely broken event flow or data-loss path; High = correctness, durability, or isolation; Medium = maintainability, operational reliability; Low = polish; Later = production-only/scaling decision. **Disposition:** FIX / IMPROVE / VERIFY / KEEP LOCAL / LATER.

### A. Infrastructure, state and security

| ID | Severity | Evidence / impact | Action |
|---|---|---|---|
| INF-01 | **High · C** | MySQL mounts init SQL but not `/var/lib/mysql`; container recreation can lose database files | **FIX:** named MySQL data volume, backup/restore test |
| INF-02 | **High · C** | One `admin` MySQL account has privileges on orders/payments/inventory DBs; logical isolation lacks privilege isolation | **FIX:** per-service DB users and least-privilege grants; one server is fine locally |
| INF-03 | Later · C | Kafka 7.4.1 uses ZooKeeper; older deployment architecture | **LATER:** plan KRaft migration; treat persistent-data migration carefully |
| INF-04 | Medium · C | Kafka advertises `kafka:9092` despite publishing host port 9092; host Kafka clients can fail metadata resolution | **IMPROVE:** internal/external listeners only if host clients are needed |
| INF-05 | Medium · C | Kafka `depends_on: zookeeper` ensures start order, not healthy ZooKeeper; independent Compose projects lack cross-project readiness gating | **IMPROVE:** explicit startup/recovery contract and integration tests |
| INF-06 | High before prod · C | MySQL root/admin credentials hardcoded; Redis and infrastructure ports exposed to host; Redis authentication not shown | **FIX/P:** environment secrets, minimize published ports, bind local-only where appropriate; document local exceptions |
| INF-07 | Medium · C | `--create --if-not-exists` doesn't reconcile existing partitions, replication or retention | **IMPROVE:** topic describe/validate script, detect drift without destructive edits |
| INF-08 | High · C | Only `order.events.v1.DLT` is created; actual consumers also target payment/inventory DLTs | **FIX:** provision destinations according to actual consumer error-handler policy |
| INF-09 | High verification · V | Three partitions but event-key semantics depend on `DomainEvent.getAggregateId()` for each event type | **VERIFY:** all workflow events use order ID where per-order ordering matters |
| INF-10 | KEEP LOCAL · C | One broker, RF=1, offsets RF=1; no broker-level HA | **KEEP LOCAL; LATER:** define production HA separately |
| INF-11 | Medium · C | MySQL `init.sql` runs only on fresh datadir; no ongoing DB/user/schema migration strategy shown | **IMPROVE:** bootstrap vs migration ownership; consider Flyway for app schema |
| INF-12 | Medium · C | Compose repeats application-level Kafka, Eureka, Actuator, DB and topic settings | **IMPROVE:** configuration ownership matrix below |
| INF-13 | High · C | Compose `ORDER_EVENTS`, etc. are not automatically equivalent to `app.topics.order-events` or shared constants | **FIX:** one binding contract; remove ineffective environment entries |
| INF-14 | Verify · V | Notification has no DB environment settings; may be intentionally stateless | **VERIFY:** notification delivery/persistence requirements; don't add DB without need |
| INF-15 | Medium · C | Business Compose has no health checks/restart policies or explicit dependency recovery policy | **IMPROVE:** readiness, restart and recovery tests; avoid false confidence from health alone |
| INF-16 | Medium · C | All business ports published to host despite Gateway being intended client entry point | **IMPROVE:** only publish required local-debug/API ports |
| INF-17 | Medium · C | No explicit CPU/memory policy shown for business services | **IMPROVE:** set controlled local test budgets when performance/failure testing begins |
| INF-18 | Medium · C | Infrastructure Compose owns `kafka-network`, Applications references it as external; lifecycle is implicit | **IMPROVE:** document owner and network creation/removal sequence |

### B. Kafka topic topology, publishing and error paths

| ID | Severity | Evidence / impact | Action |
|---|---|---|---|
| INF-19 | **Critical · C** | Notification subscribes to `order.events`, `payment.events`, `inventory.events`; producers and init use `.v1`; broker auto-creation disabled | **FIX:** subscribe to versioned topics via one property/constant contract |
| INF-20 | **Critical · C** | Notification fallback sends to `notification.events.dlq`; script creates neither that topic nor a matching DLT | **FIX:** define and provision actual failure destination; choose naming convention |
| INF-21 | **High · C** | `KafkaTemplate.send()` futures ignored in Order/Payment/Inventory and Notification fallback; Payment logs “published” before ack | **FIX:** completion handling and publication failure policy; consider outbox for DB + event atomicity |
| INF-22 | **Critical risk · R** | Order fallback only logs then returns; Kafka container can regard handled listener invocation as successful | **FIX:** don't swallow retryable failures; use Spring Kafka recovery and verify offsets |
| INF-23 | **High risk · V** | Inventory/Notification use Resilience4j THREADPOOL bulkhead on Kafka listeners; async AOP/return-type compatibility and ack semantics unproven | **VERIFY/FIX:** test proxy startup, execution and offset behavior; prefer synchronous Kafka listener lifecycle |
| INF-24 | **High risk · R** | Listeners call `alreadyProcessed()` before business operation; earlier guard implementation marked event processed before work | **FIX:** transactional idempotency boundary; handle payment external side effect separately |
| INF-25 | Medium · C | Topic names via shared constants (Order/Payment), `@Value` (Inventory), hardcoded legacy strings (Notification) | **IMPROVE:** consistent topic naming and binding strategy |
| INF-26 | **High verification · V** | Order has two `@KafkaListener` methods on `inventory.events.v1` with same group, each expecting a different event subtype | **VERIFY/FIX:** separate same-group listener containers are competing consumers, **not** subtype dispatch; one listener with explicit routing or `@KafkaHandler` class-level listener needed |
| INF-27 | **High · C** | Resilience4j `@Retry` overlaps Spring Kafka `DefaultErrorHandler` retries; fallback can prevent handler from seeing exceptions | **FIX:** define single retry owner for Kafka delivery, separate business/provider retries |
| INF-28 | Medium · C | Consumer factory `*-service-group` differs from listener `groupId="*-service"`; annotation overrides for those listeners | **IMPROVE:** one effective consumer-group source and clear group ownership |
| INF-29 | Medium · C | MDC fields/cleanup differ; `MDC.clear()` removes all current MDC including context created upstream | **IMPROVE:** scoped MDC keys and consistent metadata/logging conventions |
| INF-30 | **High risk · R** | Payment fallback emits `PaymentFailedEvent` for technical failures, potentially turning outages into business failures | **FIX:** distinguish business declines from transient/unknown technical outcomes |
| INF-31 | **Critical risk · R** | Inventory publisher circuit-breaker fallback logs and returns, hiding event publication failure | **FIX:** propagate failure or persist for guaranteed retry/outbox |
| INF-32 | Verify · V | Notification listener shows no deduplication; external side effects could be repeated | **VERIFY:** delivery semantics, idempotency keys and external provider behavior |
| INF-33 | Medium · C | Order/Payment/Inventory configure `DefaultErrorHandler`, Notification custom listener factory does not | **FIX:** consistent error-handler policy and deserialization recovery |

### C. Kafka factory configuration, serialization and event contract

| ID | Severity | Evidence / impact | Action |
|---|---|---|---|
| INF-34 | **High · C** | Bootstrap resolution varies: environment/localhost, `KafkaProperties`, or hardcoded Docker hostname | **FIX:** resolve through Spring Boot config consistently |
| INF-35 | **High · C** | Custom `new HashMap<>()` factories bypass KafkaProperties-derived producer/consumer settings not explicitly copied | **FIX:** prefer Boot auto-config or build maps from `KafkaProperties` then override selectively |
| INF-36 | **High · C** | Order error handler sends Payment and Inventory failures to `.DLT` topics not provisioned | **FIX:** DLT topology matches actual consumed source topics |
| INF-37 | Medium · C | DLT naming based only on source topic, potentially shared by distinct consumer groups | **IMPROVE:** define DLT ownership/replay policy per consumer group where needed |
| INF-38 | **High · C** | Order/Payment disable JSON type headers; Inventory/Notification do not; consumer uses `JsonDeserializer<Object>` | **FIX:** one explicit polymorphic event deserialization contract |
| INF-39 | **High · C** | Notification hardcodes broker, has no `ErrorHandlingDeserializer` or custom `DefaultErrorHandler`, concurrency 1 with three-partition topics | **FIX:** align with chosen consumer pattern; concurrency 1 ≠ global ordering |
| INF-40 | **Critical verification · V** | `JsonDeserializer<Object>` plus disabled type headers and method-specific payloads do not prove correct subtype resolution | **VERIFY/FIX:** Testcontainers end-to-end tests with real events and both success/failure types |

**Additional confirmed nuance (included in INF-21/31/40):** A `DeadLetterPublishingRecoverer` with `KafkaTemplate<String, DomainEvent>` may be unable to republish malformed raw bytes or a `DeserializationException` payload; test DLT publication of invalid JSON with an appropriate serializer/template strategy. A recoverer should verify DLT send success before a record is considered recovered. Kafka `enable.auto.commit=false` does **not** mean application-controlled manual acknowledgments; Spring Kafka container ack mode and error-handler behavior must be tested.

**Important correction to simplistic reasoning:** two distinct `@KafkaListener` methods in the same consumer group and same topic **divide partitions/records**; Kafka does not choose the method based on Java parameter type. This can cause incorrect subtype delivery even if JSON deserialization is fixed. Also, producer keys align ordering **only within one topic partition**, not across the separate Order/Payment/Inventory topics.

## Configuration ownership matrix — proposed standard

| Concern | Source of truth | Java role | Compose role | Avoid |
|---|---|---|---|---|
| Broker image, broker IDs, listener endpoints, data volumes | Infrastructure Compose | None | Own broker topology | Hardcoded hostnames in factories |
| Topic names / contracts | Shared events contract + application topic properties | Typed properties or constants | No repeated topic env entries unless true overrides | Three divergent naming systems |
| Topic partitions, replication, retention, DLT creation | Kafka provisioning script/config | No auto-creation | Run init job | Treat `--if-not-exists` as drift reconciliation |
| Client bootstrap addresses | Spring `spring.kafka.bootstrap-servers` | Consume resolved `KafkaProperties` | Supply environment-specific value if needed | `System.getenv()` in client factory |
| Serializer/deserializer and error policy | Spring app config + minimal Kafka configuration beans | Implement custom subtype/error behavior only if needed | None | Inconsistent type-header policy |
| Producer acks, retries, idempotence | `spring.kafka.producer.*` | Custom only for special cases | None | Manual maps silently ignoring YAML |
| Consumer groups / concurrency | Service application config | Listener annotation references properties | None except scale override | Mismatched group IDs |
| Service datasource URL/user | `application-{profile}.yml` plus secrets/env | Spring Data/JPA | Supply deployment-specific secrets | Shared admin user |
| Schema evolution | Service-owned Flyway/Liquibase migrations (if adopted) | Migration scripts per service | DB initialization only | Hibernate `ddl-auto=update` as prod migration |
| Metrics, tracing and health | Shared conventions + service config | Instrument code where required | Deploy collectors | Duplicated/inert env vars |
| Secrets | Environment/secret management | Read via Spring configuration | Inject secrets | Commit credentials |

### Suggested application config model

```yaml
# application.yml — common defaults, not Docker hostnames
spring:
  kafka:
    producer:
      acks: all
      properties:
        enable.idempotence: true
    consumer:
      enable-auto-commit: false
app:
  kafka:
    topics:
      order-events: order.events.v1
      payment-events: payment.events.v1
      inventory-events: inventory.events.v1
      notification-events: notification.events.v1
```

```yaml
# application-docker.yml — deployment-specific hostnames
spring:
  kafka:
    bootstrap-servers: kafka:9092
```

Illustrative only: the final `@ConfigurationProperties` prefix/field names must match the code; do not copy into every service blindly. `KafkaProperties.buildProducerProperties(...)` and `buildConsumerProperties(...)` signatures vary by Spring Boot version; use the Spring Boot 3.3.3 API. Spring Boot auto-configuration is the preferred baseline, but polymorphic event handling and custom error recovery may justify explicit beans.

## Kafka topic and DLT topology

### Current declared topics

| Topic | Partitions | RF | Retention | Status |
|---|---:|---:|---|---|
| `order.events.v1` | 3 | 1 | 7 days explicit | Provisioned |
| `order.events.v1.DLT` | 3 | 1 | 14 days explicit | Provisioned |
| `payment.events.v1` | 3 | 1 | Broker default | Provisioned |
| `inventory.events.v1` | 3 | 1 | Broker default | Provisioned |
| `notification.events.v1` | 3 | 1 | Broker default | Provisioned, but no supplied publisher uses it as a normal workflow topic |

### Effective consumer destinations (from supplied code)

| Consumer | Input | Failure route | Exists in script? | Assessment |
|---|---|---|---|---|
| Payment | `order.events.v1` | `order.events.v1.DLT` | Yes | Provisioned; test serialization/recovery |
| Order | `payment.events.v1` | `payment.events.v1.DLT` | No | Missing |
| Order | `inventory.events.v1` | `inventory.events.v1.DLT` | No | Missing |
| Inventory | `payment.events.v1` | `payment.events.v1.DLT` | No | Missing; shares topic-based DLT with Order |
| Notification | `order.events`, `payment.events`, `inventory.events` | `notification.events.dlq` only via Resilience4j fallback | No | **Wrong source topics and missing destination** |

**Recommended decision:** Define DLTs around **consumer recovery ownership**, not merely topic names. Two valid approaches: (A) source-topic `.DLT` for simple local setup, with a documented mixed-consumer replay process; (B) source-topic + consumer-group DLT for distinct ownership and replay. Choose one deliberately. For any destination retaining the source partition index, provision at least as many partitions as the source. Decide retention, alerting, inspection, redrive, poison-pill policy and idempotent replay before calling DLT handling complete.

**Potentially subtle:** `order.events.v1.DLT` is a *Payment consumer* failure route, not a generic “Order Service DLT.” Topic naming alone does not convey ownership.

## Event-flow correctness contract

1. Order publishes `OrderCreatedEvent` keyed by `getAggregateId()` to `order.events.v1`.
2. Payment consumes the Order topic in `payment-service`, attempts processing, publishes completed/failed to `payment.events.v1`.
3. Inventory consumes Payment completed events in `inventory-service`, reserves stock, publishes reserved/failed to `inventory.events.v1`.
4. Order consumes Payment failure and Inventory outcome events, transitions Order state, publishes completed/failed to `order.events.v1`.
5. Notification *intends* to consume all three streams but currently subscribes to legacy names.

**Must verify in Phase 1:** (a) all event `getAggregateId()` implementations use the intended order ID; (b) JSON polymorphic type handling works without relying on inconsistent headers; (c) Order has a **single correct dispatch mechanism** for multiple Inventory event types; (d) Payment listener ignores other Order event types safely; (e) Inventory listener ignores Payment failed events safely; (f) consumer-group offset behavior after fallback and DLT publish; (g) event metadata version/correlation preservation; (h) business-state idempotency and publication are failure-safe. The current per-topic event polymorphism is a cross-service architecture issue; test it before any production-readiness claim.

## Proposed `setup/` structure

```text
setup/
├── README.md                         # startup, shutdown, ports, recovery, reset rules
├── .env.example                      # nonsecret sample values
├── 1.infrastructure/
│   ├── compose.yml
│   ├── kafka/
│   │   ├── create-topics.sh
│   │   └── verify-topics.sh          # Phase 1
│   ├── mysql/
│   │   └── init.sql
│   └── redis/                        # only if config files become necessary
├── 2.platform/
│   └── compose.yml                   # Eureka + API Gateway
├── 3.observability/
│   ├── compose.yml                   # Prometheus, Grafana, Alertmanager, Zipkin, exporter
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   └── rules/
│   ├── alertmanager/
│   │   └── alertmanager.yml
│   ├── grafana/
│   │   ├── provisioning/
│   │   │   ├── datasources/
│   │   │   └── dashboards/
│   │   └── dashboards/
│   └── logging/                      # only when centralized logging is adopted
└── 4.applications/
    └── compose.yml                   # Order, Payment, Inventory, Notification
```

**Renames:** `3.monitoring` → `3.observability`; `4.services` → `4.applications`; normalize compose file names to `compose.yml` if scripts and documentation are updated together. `1.infrastructure` and `2.platform` are sensible names; keep numeric prefixes while they help manual startup. Move Zipkin and Kafka Exporter from Platform to Observability when adjusting monitoring targets and networks. Do **not** create empty folders just to match the diagram. Keep `shared-events` as a Maven library, not a containerized service.

**Network:** `1.infrastructure` can remain owner of `kafka-network` and other projects use `external: true`. Document that startup requires the network to exist, that Compose `down` behavior may differ while attached services are running, and that removing it breaks other layers. Alternatively provision the network once in a script; avoid accidental competing ownership.

## Local vs production decisions

| Decision | Local development | Production target / condition |
|---|---|---|
| Kafka brokers | 1 broker, RF=1 acceptable | Multiple brokers, RF/min ISR and durability policy based on SLO |
| ZooKeeper | Keep temporarily for audit stability | Plan supported KRaft migration, including volume/data migration |
| MySQL | One server, separate DBs/users | Managed DB, backups, HA, least privilege, migrations |
| Redis | One container for gateway rate limit | HA/security/persistence determined by actual state requirements |
| Networking | Shared bridge + internal plaintext | Network isolation, TLS/auth where warranted |
| Secrets | Local `.env` with noncommitted values | Managed secrets and rotation |
| Topic init | Script plus verification | Declarative reconciliation/change management |
| Kafka listener retries | Spring Kafka handler with bounded retries and DLT | Explicit retry/backoff/replay/alert policies |
| Event publishing | Completion callbacks + controlled failure path | Transactional outbox for DB + event workflows where required |
| Orchestration | Four Compose projects | ECS/Kubernetes only after deployment requirements justify them |
| Host ports | Publish only what local debugging needs | Expose Gateway/approved ingress; internalize services |
| Observability | Local Prometheus/Grafana/Zipkin | SLO-driven metrics, logs, traces, alert routing |

## Ordered Phase 1 plan

| Step | Priority | Change | Acceptance criteria |
|---|---|---|---|
| P1-01 | **P0** | Create backup of current MySQL data; add MySQL named volume | Recreate MySQL container without losing test data; fresh bootstrap documented |
| P1-02 | **P0** | Repair Notification subscriptions and define actual DLTs | Notification receives real `.v1` workflow events; no unknown-topic errors |
| P1-03 | **P0** | Fix event polymorphism and listener routing | All Order/Payment/Inventory event variants deserialize and reach correct handlers in integration tests |
| P1-04 | **P0** | Make publication failures observable and non-silent | Inject broker failure; send future fails visibly and workflow doesn't falsely report success |
| P1-05 | **P0** | Remove swallowing fallbacks and settle retry ownership | A failed listener is retried/recovered by Spring Kafka; offsets not advanced before safe outcome |
| P1-06 | **P0** | Correct idempotency/business transaction boundaries | Crash/redelivery tests don't skip unfinished work or duplicate irreversible side effects |
| P1-07 | **P1** | Standardize Kafka configuration ownership and serializers | No hardcoded broker in factories; YAML overrides reach producer and consumer; consistent type policy |
| P1-08 | **P1** | Define consumer-group-aware DLT policy and provision/verify topics | Every actual error-handler destination exists; malformed JSON and replay tested |
| P1-09 | **P1** | Introduce per-service MySQL accounts | Order cannot read/write Payment or Inventory DB, and vice versa |
| P1-10 | **P1** | Separate bootstrap from schema migrations | Repeatable clean bootstrap; schema changes tracked and safe on persisted DB |
| P1-11 | **P1** | Consolidate `application.yml`/Docker profiles/Compose vars | Configuration ownership matrix reflected in code; no inert topic env vars |
| P1-12 | **P1** | Rename/reorganize `setup/` and move observability services | All four Compose layers start, scrape and resolve names after move |
| P1-13 | **P1** | Health/restart/startup/network/ports documentation | Cold-start and dependency-recovery runbook passes on Windows Docker Desktop |
| P1-14 | **P2** | Topic configuration drift verification | Script reports unexpected partition/retention/RF without silently mutating topics |
| P1-15 | **P2** | Kafka and DB failure-injection tests | Broker stop, DB stop, bad payload, duplicate delivery, DLT replay have documented outcomes |
| P1-16 | **P2** | Local secrets and resource limits | No committed production-like passwords; repeatable constrained load tests |
| P1-17 | **LATER** | KRaft, HA, TLS, managed databases, production orchestration | Implement only against explicit deployment/SLO requirements |

**Ordering note:** P1-02 and P1-03 should be tested together; changing topic names without fixing type dispatch can move the failure rather than solve it. P1-04 through P1-06 are a coordinated reliability redesign; avoid patching one in isolation. Do not delete/recreate MySQL volumes as a migration technique.

## Required integration / failure tests

| Scenario | Expected evidence |
|---|---|
| Publish all Order event subtypes | Correct Payment/Notification handlers; no ClassCast/deserialization failures |
| Publish both Payment event subtypes | Inventory processes completed only; Order processes failed only |
| Publish both Inventory event subtypes | Order dispatches correctly and exactly once logically |
| Notification subscribes to versioned topics | Consumes from `order.events.v1`, `payment.events.v1`, `inventory.events.v1` |
| Invalid JSON to each consumed topic | Recoverer handles deserialization safely; DLT destination exists; record not silently lost |
| Stop Kafka before producer ack | Publish future reports failure; no false “published” confirmation |
| Fail business operation after idempotency marker | Redelivery resumes or compensates correctly, not skipped |
| Throw from listener with fallback active | Verify actual Spring Kafka retry, recovery and committed offset behavior |
| Force DLT broker send failure | Original record not treated as safely recovered |
| Recreate MySQL container | Data preserved after volume change |
| Change topic config on existing topic | Drift validator reports mismatch |
| Restart each Compose layer in isolation | Dependencies reconnect; health/alerts reflect recovery |
| Two services fail on same source topic | DLT ownership and redrive are unambiguous |

## Phase 1 engineering guardrails

1. **Single owner per configuration property.** Docker injects environment; Spring resolves properties; Java consumes resolved configuration.
2. **No silent success.** A swallowed listener/producer exception or unchecked send future must not masquerade as completed work.
3. **Kafka is at-least-once in this design.** Design idempotent state transitions, reliable publication and safe replay; do not claim exactly-once end-to-end business behavior.
4. **Business failures ≠ technical failures.** Declined payment/insufficient stock are domain outcomes; Kafka/DB/provider outages need retry, uncertainty handling or compensation.
5. **Partitioning is not workflow ordering.** Key by stable aggregate, test event routing, and design for inter-topic concurrency.
6. **DLT is an operational queue, not an archive.** Ownership, alerting, retention, replay and recovery criteria are mandatory.
7. **Local-first, production-aware.** Don't add Kafka clusters, Kubernetes, service mesh or multiple DB instances solely to appear enterprise-grade.
8. **Prove with tests.** Static config review establishes risks, not successful runtime delivery guarantees.

## Open evidence / decisions for next phase

- Inspect `DomainEvent`, each event's `getAggregateId()`, Jackson annotations/ObjectMapper, and `@KafkaHandler`/listener container behavior.
- Inspect `IdempotencyGuard`, transactional service methods, producer error callbacks and any existing outbox/transactional Kafka configuration.
- Confirm whether Notification is intentionally stateless, whether it sends external notifications, and whether `notification.events.v1` is intended for outgoing events.
- Confirm whether other consumer groups read Order/Payment/Inventory topics and whether DLT naming should be per group.
- Inspect complete application profiles and Dockerfile health tooling before finalizing health-check commands.
- Verify Docker Compose YAML as parsed (`docker compose config`) because pasted formatting contains transport artifacts; audit assumes intended YAML structure.
- Validate Spring Boot 3.3.3 / Spring Kafka version-specific deserializer and listener behavior with executable integration tests.
- Review whether `SPRING_JPA_HIBERNATE_DDL_AUTO=update` is enabled outside local; use migrations for managed environments.

## Exit criteria for Phase 0 Infrastructure audit

**Audit complete:** all supplied files reviewed, strengths recorded, findings classified, cross-service Kafka topology reconciled, target `setup/` structure proposed, and ordered Phase 1 plan defined.  
**Implementation not yet complete:** runtime behavior, serialization, retry/DLT reliability and persistence must be demonstrated in Phase 1 tests.  
**Next Phase 0 subject:** cross-service architecture/deployment integration, then testing/CI/CD/security gap consolidation, before Phase 1 implementation prioritization.
