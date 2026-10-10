# Inventory Service --- Architecture Audit

**Project:** Kafka Microservices Distributed System\
**Service:** `inventory-service`\
**Phase:** Phase 0 --- Architecture Audit

## 1. Executive Summary

The Inventory Service has a solid domain foundation: a real
`InventoryItem` aggregate, encapsulated stock reservation behaviour,
transactional mutation, database locking, persisted processed-event IDs,
Kafka event publication, retry/DLT foundations, and observability.

The main Phase 1 work is at the boundaries: idempotency transaction
scope, DB/Kafka dual writes, business-vs-technical failures, overlapping
resilience mechanisms, and deliberately choosing a concurrency strategy.

## 2. Current Workflow

``` text
PaymentCompletedEvent
 -> InventoryEventListener
 -> IdempotencyGuard
 -> INSERT ProcessedEvent
 -> InventoryService.reserveStock()
 -> PESSIMISTIC_WRITE product row
 -> InventoryItem.reserve(quantity)
 -> DB commit
 -> InventoryEventPublisher
 -> InventoryReservedEvent
```

## 3. KEEP --- Strong Foundations

-   Keep the `InventoryItem` aggregate and `reserve()` domain behaviour.
-   Keep transactional stock reservation.
-   Keep database-backed idempotency as a concept.
-   Keep the current pessimistic-locking approach until Phase 1
    deliberately compares it with optimistic locking.
-   Keep Spring Kafka `DefaultErrorHandler` and DLT infrastructure as
    the basis of consumer recovery.
-   Keep Actuator, Prometheus, tracing, Zipkin, Kafka observation, and
    MDC correlation metadata.
-   Keep event serialization policy in `shared-events`; do not turn that
    module into a generic Spring configuration dumping ground.

## 4. HIGH PRIORITY --- Idempotency Transaction Boundary

Current processing effectively uses separate transactions:

``` text
TX #1: INSERT ProcessedEvent -> COMMIT
TX #2: reserve inventory     -> COMMIT
```

Failure scenario:

``` text
ProcessedEvent saved = SUCCESS
Inventory reservation = FAILURE
Kafka redelivery       = SKIPPED AS DUPLICATE
```

The event can therefore be recorded as processed before its business
effect succeeds.

### Phase 1 action

Make the processed-event claim and inventory mutation part of the same
local transaction. `ProcessedEvent` should mean the event was
successfully incorporated into Inventory Service state, not merely that
processing started.

## 5. HIGH PRIORITY --- DB/Kafka Dual Write

Current path:

``` text
reserve inventory
 -> DB COMMIT
 -> publish InventoryReservedEvent
```

If Kafka publication fails after the database commit, stock changes but
downstream services may never learn about the reservation.

### Phase 1 action --- Transactional Outbox

Target:

``` text
ONE LOCAL DB TRANSACTION
+ ProcessedEvent
+ Inventory stock mutation
+ OutboxEvent
        |
      COMMIT
        |
Outbox Publisher
        |
      Kafka
```

## 6. HIGH PRIORITY --- Business vs Technical Failure

Insufficient stock is a legitimate business outcome.
MySQL/Kafka/infrastructure failure is a technical processing failure.

Target:

``` text
Insufficient stock
 -> explicit domain failure
 -> InventoryReservationFailedEvent

Technical failure
 -> propagate exception
 -> Spring Kafka retry
 -> DLT after recovery exhausted
```

### Phase 1 actions

-   Replace generic `IllegalStateException("Insufficient stock")` with
    explicit domain semantics such as `InsufficientStockException`.
-   Do not convert arbitrary infrastructure failures into
    `InventoryReservationFailedEvent`.

## 7. HIGH PRIORITY --- Retry Ownership

The listener currently combines Resilience4j `@Retry` with Spring Kafka
`DefaultErrorHandler`.

This creates nested retry behaviour.

### Phase 1 decision

Spring Kafka should own retries for Kafka-record processing. Remove
Resilience4j `@Retry` from the Kafka listener unless a separate
operation later has a clearly defined need for it.

## 8. HIGH PRIORITY --- Circuit Breaker/Fallback Behaviour

The listener fallback can swallow a technical exception and publish a
business failure event, preventing the original processing failure from
naturally reaching Kafka recovery.

The producer fallback also logs publication failure and returns, which
is not a durable delivery strategy.

### Phase 1 actions

-   Allow technical listener failures to propagate to
    `DefaultErrorHandler`.
-   Remove fallback behaviour that turns infrastructure failure into
    business failure.
-   Move durable event publication responsibility to the outbox pattern.

## 9. CONCURRENCY --- Current Model

Current stack:

``` text
Kafka partitions
 -> listener concurrency = 3
 -> ThreadPoolBulkhead (4–8 threads + queue)
 -> @Transactional
 -> PESSIMISTIC_WRITE
 -> @Version optimistic locking
```

This is too many concurrency controls without explicit ownership.

### Important current correctness result

With stock = 1 and two different orders concurrently reserving the same
product, `PESSIMISTIC_WRITE` serializes access to the product row. One
transaction can reserve the final unit; the next sees stock = 0 and
fails. The simple negative-stock race is therefore protected.

## 10. Pessimistic + Optimistic Locking

The entity has:

``` java
@Version
private Long version;
```

while the repository uses:

``` java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<InventoryItem> findByProductId(UUID productId);
```

### Phase 1 action

Choose and document the intended strategy instead of retaining both
accidentally.

Do not automatically remove pessimistic locking. Inventory reservation
is a legitimate domain where pessimistic locking may be defensible under
contention. Compare it with optimistic locking through concurrent tests
before deciding.

## 11. ThreadPoolBulkhead

Kafka already has partition/consumer concurrency. The ThreadPoolBulkhead
introduces another executor and queue.

### Phase 1 action

Remove it unless a concrete requirement demonstrates what it solves
beyond Kafka consumer concurrency/backpressure.

## 12. Kafka Ordering Does Not Serialize by Product

Events are keyed by aggregate/order ID. Two different orders for the
same product may therefore be processed on different Kafka partitions
simultaneously.

``` text
Order A -> Product X -> partition 0
Order B -> Product X -> partition 2
```

Database-level product concurrency protection remains necessary.

## 13. Domain Invariant Improvements

`InventoryService` validates `quantity > 0`, but
`InventoryItem.reserve()` itself does not.

Calling `reserve(-10)` could increase stock mathematically.

### Phase 1 actions

-   Validate positive quantity inside the aggregate behaviour.
-   Replace Lombok `@Data` with deliberate accessors so callers cannot
    bypass `reserve()` through arbitrary setters.
-   If one inventory row per product is intended, enforce a database
    unique constraint on `productId`.

## 14. Kafka Configuration Duplication

Kafka configuration exists in both Java and YAML, including bootstrap
servers, producer retries/acks, consumer groups,
serializers/deserializers, and consumer behaviour.

### Phase 1 action

Establish one clear configuration authority and reduce duplication,
using Spring Boot configuration/`KafkaProperties` where appropriate.

## 15. Consumer Group Mismatch

Three definitions currently exist:

``` text
application.yml      -> inventory-service
KafkaConsumerConfig  -> inventory-service-group
@KafkaListener       -> inventory-service
```

### Phase 1 action

Define consumer-group ownership once.

## 16. Bootstrap Server Mismatch

Configuration currently includes:

``` text
application.yml -> kafka:9092
consumer Java   -> localhost:9092 fallback
producer Java   -> KafkaProperties
```

### Phase 1 action

Keep environment-specific broker addresses in profile/environment
configuration instead of separate Java fallbacks.

## 17. Topic Configuration Duplication

Topic names exist both in `shared-events Topics` constants and
`app.topics.*` YAML properties.

### Phase 1 action

Choose one authoritative strategy. If topic names are environment
configuration, prefer typed `@ConfigurationProperties` over scattered
`@Value` fields.

Remove the unused `paymentEventsTopic` field.

## 18. DLT Configuration

DLT names exist explicitly in YAML, while the recoverer derives them
with:

``` java
record.topic() + ".DLT"
```

Choose either convention-derived or explicit naming.

Because Kafka auto-topic creation is disabled, verify that DLT topics
are explicitly provisioned with compatible partition counts, retention,
monitoring, and a documented replay/recovery procedure.

## 19. Serialization

The shared `EventObjectMapperFactory` plus a local Spring bean is
acceptable.

Do not move generic Spring service configuration into `shared-events`
merely to eliminate small configuration classes.

`trusted.packages="*"` should be restricted during production hardening
to expected event-contract packages.

## 20. Resilience Configuration Loading

`application-resilience.yml` exists, but the audited files do not prove
that it is imported or activated.

### Phase 1 action

Verify and make explicit how `application-resilience.yml` is loaded.

## 21. Observability

Keep the current observability architecture.

For production-like environments, make tracing sampling configurable
rather than assuming `probability: 1.0`.

## 22. Event Versioning

The listener currently contains a literal event version:

``` java
next(event.metadata(), "inventory-service", 1)
```

### Phase 1 action

Remove magic version literals and give event-schema version ownership an
explicit place in the event contract.

## 23. Recommended Phase 1 Target Workflow

``` text
PaymentCompletedEvent
        |
        v
Inventory application transaction
        |
        +-- idempotent event claim
        +-- load InventoryItem using chosen concurrency strategy
        +-- reserve stock
        +-- create OutboxEvent
        +-- record successful processing
        |
      COMMIT
        |
        v
Outbox Publisher
        |
        v
Kafka
```

Expected business failure:

``` text
Insufficient stock
 -> explicit domain outcome
 -> persist outcome/outbox atomically
 -> InventoryReservationFailedEvent
```

Technical failure:

``` text
DB/infrastructure failure
 -> exception propagates
 -> DefaultErrorHandler
 -> retry
 -> DLT if exhausted
```

## 24. Phase 1 Action Checklist

### Priority 1 --- Reliability

-   [ ] Make idempotency and inventory mutation one local transaction.
-   [ ] Introduce Transactional Outbox.
-   [ ] Align `ProcessedEvent` semantics with successful processing.
-   [ ] Separate insufficient-stock business failure from technical
    failure.
-   [ ] Introduce explicit insufficient-stock domain semantics.
-   [ ] Let technical consumer failures reach Spring Kafka.
-   [ ] Remove Resilience4j retry from Kafka listener processing.
-   [ ] Remove fallback behaviour that bypasses Kafka recovery.
-   [ ] Replace producer logging fallback with durable outbox
    publication.

### Priority 2 --- Concurrency

-   [ ] Deliberately choose/document the locking strategy.
-   [ ] Compare `PESSIMISTIC_WRITE` with `@Version`.
-   [ ] Test two orders competing for the final unit.
-   [ ] Test multiple reservations for the same product.
-   [ ] Remove ThreadPoolBulkhead unless justified.
-   [ ] Keep Kafka concurrency conceptually separate from product-row
    concurrency.

### Priority 3 --- Domain Model

-   [ ] Validate positive quantity inside `InventoryItem.reserve()`.
-   [ ] Replace generic insufficient-stock exception.
-   [ ] Replace `@Data` with deliberate accessors.
-   [ ] Prevent uncontrolled `available` mutation.
-   [ ] Enforce unique `productId` if required by the domain.

### Priority 4 --- Kafka / Configuration

-   [ ] Consolidate Kafka configuration ownership.
-   [ ] Remove conflicting consumer-group definitions.
-   [ ] Remove inconsistent bootstrap-server fallbacks.
-   [ ] Choose one topic-name strategy.
-   [ ] Remove unused `paymentEventsTopic`.
-   [ ] Choose one DLT naming strategy.
-   [ ] Verify DLT provisioning/partition compatibility.
-   [ ] Restrict trusted JSON packages.
-   [ ] Verify `application-resilience.yml` loading.
-   [ ] Replace magic event version `1`.

### Priority 5 --- Production Hardening

-   [ ] Introduce database migrations.
-   [ ] Add DLT monitoring and replay procedure.
-   [ ] Add reservation success/failure metrics.
-   [ ] Add retry/DLT metrics and alerts.
-   [ ] Make tracing sampling environment-specific.
-   [ ] Load-test popular-product contention.

## 25. Recommended Phase 1 Tests

### Duplicate event

Same `eventId` delivered twice.

Expected: stock decreases once and only one logical outcome is produced.

### Concurrent duplicate event

Two threads process the same `eventId`.

Expected: only one performs the business mutation.

### Two orders, one unit

``` text
available = 1
Order A quantity = 1
Order B quantity = 1
```

Expected: one succeeds, one gets insufficient-stock outcome, stock never
becomes negative.

### Failure after event claim

Force business processing to fail after idempotency registration.

Expected after refactor: the transaction rolls back and the event
remains retryable.

### Kafka unavailable after DB mutation

Expected after outbox: inventory and outbox commit together; the event
remains pending and publishes when Kafka recovers.

### Database unavailable

Expected: no business failure event is produced merely because
infrastructure is unavailable; Kafka retry/recovery handles it.

## 26. Architecture Lessons

**Idempotency and resource concurrency solve different problems.**

``` text
ProcessedEvent uniqueness -> protects SAME EVENT
Inventory row locking     -> protects SAME PRODUCT
```

**Kafka ordering does not automatically protect database resources.**

**A DB commit does not guarantee distributed event delivery.**

``` text
DB COMMIT != Kafka publication
```

**Business failure and infrastructure failure require different
handling.**

**More resilience mechanisms do not automatically create more
resilience.** Clear ownership of retry, concurrency, publication, and
recovery is more important.

## 27. Final Phase 0 Assessment

The Inventory Service should not be rewritten from scratch.

Preserve:

``` text
InventoryItem
InventoryService
InventoryRepository
reserve() domain behaviour
transactional stock mutation
database-backed idempotency concept
stock concurrency protection
Kafka integration
observability
```

Refactor the boundaries around those components.

The central Phase 1 transformation is:

``` text
CURRENT

ProcessedEvent commit
 -> Inventory commit
 -> Kafka publish
```

to:

``` text
TARGET

ONE LOCAL TRANSACTION
 + ProcessedEvent
 + Inventory mutation
 + OutboxEvent
        |
      COMMIT
        |
   Outbox -> Kafka
```

combined with:

``` text
business failure -> domain failure event
technical failure -> Kafka retry/recovery/DLT
```

------------------------------------------------------------------------

## Phase 0 Status

**Inventory Service architecture audit: COMPLETE**

**Next lifecycle stage:** Phase 1 --- Refactoring and reliability
implementation.
