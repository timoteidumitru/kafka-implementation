# Order Service — Architecture Audit

**Service:** `order-service`  
**Phase:** Phase 0 — Architecture Audit  
**Status:** Complete  
**Purpose:** Record the current architecture, confirmed issues, improvement opportunities, and Phase 1 actions without implementing the fixes during the audit.

---

# 1. Current Responsibility

The Order Service is the main public business entry point for the ordering workflow.

## Receives

```text
POST /api/orders
```

through the API Gateway.

## Creates

```text
Order
└── status = CREATED
```

and persists it in `orders_db`.

## Produces

```text
order.events.v1

├── OrderCreatedEvent
├── OrderCompletedEvent
└── OrderFailedEvent
```

## Consumes

```text
payment.events.v1
└── PaymentFailedEvent

inventory.events.v1
├── InventoryReservedEvent
└── InventoryReservationFailedEvent
```

## Simplified Current Flow

```text
Client
  │
  ▼
API Gateway
  │
  ▼
POST /api/orders
  │
  ▼
OrderController
  │
  ▼
OrderService
  │
  ▼
orders_db
  │
  ▼
OrderCreatedEvent
  │
  ▼
Kafka
```

Subsequent events determine the final Order state.

### Payment failure

```text
PaymentFailedEvent
       │
       ▼
Order → CANCELLED
       │
       ▼
OrderFailedEvent
```

### Inventory failure

```text
InventoryReservationFailedEvent
       │
       ▼
Order → CANCELLED
       │
       ▼
OrderFailedEvent
```

### Inventory success

```text
InventoryReservedEvent
       │
       ▼
Order → COMPLETED
       │
       ▼
OrderCompletedEvent
```

---

# 2. KEEP — Existing Good Decisions

## Service Boundary

- Order Service is the public business entry point for order creation.
- `/api/orders` provides a clear API boundary.
- Downstream business communication is asynchronous through Kafka.
- Order Service owns its own database state.
- Other internal services do not need direct REST access to Order for the currently audited workflow.

## Domain Model

- `Order` exists as a persistent aggregate/entity.
- UUID is used as the Order identity.
- New Orders explicitly begin with `CREATED`.
- Lifecycle operations are represented through methods:
  - `markCompleted()`
  - `markCancelled()`
- `OrderStatus` uses `EnumType.STRING`.
- `OrderRepository` remains appropriately simple.

## Event Architecture

- Kafka records use `orderId` / aggregate ID as their key.
- Events contain metadata.
- `correlationId` is propagated across related events.
- Event metadata contains:
  - `eventId`
  - `correlationId`
  - `occurredAt`
  - `sourceService`
  - `version`
- `OrderEventPublisher` provides an abstraction over `KafkaTemplate`.
- Order Service publishes explicit lifecycle events:
  - `OrderCreatedEvent`
  - `OrderCompletedEvent`
  - `OrderFailedEvent`

## Reliability Foundation

- Consumer idempotency has already been recognized as necessary.
- `ProcessedEvent.eventId` provides database-level uniqueness.
- `ErrorHandlingDeserializer` exists.
- `DefaultErrorHandler` exists.
- `DeadLetterPublishingRecoverer` exists.
- Retry/backoff exists.
- Consumer auto-commit is disabled.
- Producer uses `acks=all`.
- Listener concurrency is explicitly configured.
- A DLT naming convention exists:

```text
<original-topic>.DLT
```

## Observability

Keep the existing project-level observability direction:

- correlation IDs
- event IDs in MDC/logging
- MDC cleanup in `finally`
- Actuator
- Prometheus
- distributed tracing
- Zipkin
- health endpoints

---

# 3. HIGH PRIORITY — Consumer Idempotency Transaction Boundary

Current `IdempotencyGuard` effectively performs:

```text
incoming Kafka event
       │
       ▼
existsById(eventId)
       │
       ▼
ProcessedEvent INSERT
       │
       ▼
business operation
```

Example:

```text
InventoryReservedEvent
       │
       ▼
ProcessedEvent saved
       │
       ▼
orderService.complete()
```

This creates an important failure scenario.

Suppose:

```text
ProcessedEvent INSERT     ✓
orderService.complete()   ✗
```

The database can now contain:

```text
processed_events
eventId=A
```

while:

```text
Order
status=CREATED
```

If Kafka retries the event:

```text
alreadyProcessed(A)
       │
       ▼
true
       │
       ▼
return
```

The business operation can therefore remain incomplete.

## Phase 1 Action

Redesign the transaction boundary so the processed-event marker and relevant Order mutation participate in the appropriate atomic database transaction.

---

# 4. HIGH PRIORITY — Idempotency Race Condition

Current implementation uses:

```text
existsById(eventId)
       │
       ▼
if false
       │
       ▼
save(eventId)
```

This is a check-then-act operation.

Two concurrent executions can theoretically perform:

```text
Thread A                   Thread B

exists? false              exists? false

process                     process
```

The `eventId` primary-key constraint provides a final database-level uniqueness guarantee, but the application does not currently deliberately incorporate that uniqueness constraint into its processing semantics.

## Important Lesson

Idempotency is not simply:

```text
SELECT
then
INSERT
```

Database uniqueness should participate in the correctness guarantee.

## Phase 1 Action

Redesign consumer idempotency to safely handle concurrent duplicate delivery.

---

# 5. HIGH PRIORITY — Idempotency Does Not Solve Order Concurrency

A separate problem exists when **different events** target the same Order.

Example:

```text
InventoryReservedEvent
eventId=A
orderId=123
```

and:

```text
PaymentFailedEvent
eventId=B
orderId=123
```

Both event IDs are legitimate and unique.

Therefore:

```text
idempotency(A) → allowed
idempotency(B) → allowed
```

Two threads could potentially perform:

```text
Thread A                   Thread B

read Order 123             read Order 123

CREATED                    CREATED

markCompleted()            markCancelled()

save                       save
```

This can result in a race over the Order state.

## Important Distinction

```text
IDEMPOTENCY
"Have I already processed event A?"
```

is different from:

```text
CONCURRENCY CONTROL
"Can two operations modify Order 123 simultaneously?"
```

## Phase 1 Action

Evaluate optimistic locking, potentially using JPA `@Version`, together with explicit Order state-transition rules.

Do not introduce pessimistic locking unless the use case demonstrates that it is necessary.

---

# 6. HIGH PRIORITY — Database/Kafka Dual Write

Order state changes and Kafka publication currently occur separately.

Example:

```text
orderService.complete()
       │
       ▼
orders_db updated
       │
       ▼
publisher.publishOrderCompleted()
```

Possible failure:

```text
orders_db update       ✓
Kafka publication      ✗
```

Result:

```text
Order = COMPLETED
```

but:

```text
OrderCompletedEvent = missing
```

Downstream consumers can therefore disagree with the Order database.

The same problem exists for:

```text
OrderFailedEvent
```

and the initial order creation workflow.

---

# 7. HIGH PRIORITY — Initial Order Creation Dual Write

Current controller performs:

```text
OrderController
       │
       ▼
orderService.create(order)
       │
       ▼
publisher.publishOrderCreated(...)
```

Possible failure:

```text
Order INSERT           ✓
OrderCreatedEvent      ✗
```

The Order now exists:

```text
status = CREATED
```

but Payment/Inventory may never receive the event required to continue the workflow.

## Phase 1 Action

Evaluate and implement the Transactional Outbox pattern.

Conceptually:

```text
Database transaction
       │
       ├── save Order
       │
       └── save OutboxEvent
               │
               ▼
            COMMIT
```

followed by reliable publication from the outbox.

This should eventually cover both initial Order creation and subsequent Order lifecycle events.

---

# 8. HIGH PRIORITY — Overlapping Retry Mechanisms

Current Kafka listeners use Resilience4j:

```java
@Retry(name = "order-kafka")
```

while Kafka configuration also contains:

```java
DefaultErrorHandler
```

with:

```java
FixedBackOff(...)
```

Therefore retry ownership is duplicated:

```text
Resilience4j Retry
       +
Spring Kafka Retry/Error Handler
```

This makes the actual number and timing of attempts harder to reason about.

## Phase 1 Direction

Spring Kafka `DefaultErrorHandler` should primarily own Kafka record-processing retry/recovery.

Do not stack retry mechanisms unless each one protects a clearly different operation.

---

# 9. HIGH PRIORITY — CircuitBreaker Fallback Can Hide Kafka Failures

Current listener architecture contains:

```java
@CircuitBreaker(
    name = "order-kafka",
    fallbackMethod = "fallback"
)
```

with a fallback that logs the failure.

Conceptually:

```text
Listener throws exception
       │
       ▼
CircuitBreaker
       │
       ▼
fallback()
       │
       ▼
logs error
       │
       ▼
returns normally
```

This can interfere with:

```text
DefaultErrorHandler
       │
       ▼
retry
       │
       ▼
DLT
```

because Kafka's infrastructure needs to observe processing failures in order to apply its recovery policy.

## Phase 1 Action

Remove/reconsider the Resilience4j fallback around Kafka listener processing.

Kafka processing exceptions intended for retry/DLT handling must reach Spring Kafka.

---

# 10. DOMAIN — Order State Machine

Current states are:

```text
CREATED
COMPLETED
CANCELLED
```

and the entity provides:

```java
markCompleted()
markCancelled()
```

but those methods currently change state without validating the previous state.

This technically permits transitions such as:

```text
CREATED → COMPLETED
CREATED → CANCELLED
```

which are expected, but potentially also:

```text
COMPLETED → CANCELLED
CANCELLED → COMPLETED
COMPLETED → COMPLETED
CANCELLED → CANCELLED
```

## Phase 1 Action

Define legitimate Order state transitions.

Then enforce those transitions inside the domain model.

Do not invent additional statuses simply to make the model look more sophisticated.

---

# 11. DOMAIN — Entity Encapsulation

`Order` currently uses:

```java
@Data
```

Lombok therefore generates broad setters in addition to methods such as `equals()`, `hashCode()` and `toString()`.

This weakens the intended domain API because code can potentially bypass:

```java
markCompleted()
markCancelled()
```

and directly change fields.

## Phase 1 Action

Replace broad `@Data` usage on JPA entities with deliberately selected accessors where appropriate.

Prefer state changes through meaningful domain operations.

---

# 12. API — Request Validation

Current request:

```text
CreateOrderRequest

userId
productId
quantity
price
```

does not currently show Bean Validation constraints.

Potentially invalid requests include:

```text
userId = null
productId = null
quantity <= 0
price <= 0
```

## Phase 1 Action

Introduce validation appropriate to the actual business requirements, potentially including:

```java
@NotNull
@Positive
```

and:

```java
@Valid
```

at the controller boundary.

---

# 13. API / DOMAIN — Client Controls Price

Current trust path is:

```text
Client
  │
  ▼
CreateOrderRequest.price
  │
  ▼
Order.price
  │
  ▼
OrderCreatedEvent.price
  │
  ▼
Payment Service
  │
  ▼
amount charged
```

This means the client currently supplies the authoritative price.

That is not an appropriate eventual production trust boundary.

A client could conceptually submit:

```text
productId = expensive-product
price = 0.01
```

## Phase 1 Action

Design an authoritative pricing/amount-validation mechanism.

Do not automatically introduce another microservice merely to solve this.

The important architectural requirement is:

```text
Untrusted client
       ≠
authoritative payment amount
```

---

# 14. KAFKA — Event Serialization / Type Routing

Producer configuration currently disables type headers:

```java
JsonSerializer.ADD_TYPE_INFO_HEADERS = false
```

while consumers use:

```java
JsonDeserializer<Object>
```

At the same time, individual topics can contain multiple concrete event classes.

Example:

```text
inventory.events.v1

├── InventoryReservedEvent
└── InventoryReservationFailedEvent
```

Order Service then exposes typed listener methods for those concrete event classes.

## Phase 1 Action

Explicitly define the event serialization/type-routing strategy.

Do not change serializer configuration in isolation.

First decide how event contracts should be identified and dispatched.

---

# 15. KAFKA — Multiple Listeners / Shared Topic

Order Service contains separate listeners for:

```text
InventoryReservedEvent
```

and:

```text
InventoryReservationFailedEvent
```

while consuming the same inventory event topic/group relationship.

The exact deserialization/routing behaviour should be verified rather than assumed.

## Phase 1 Action

Review this together with the broader event serialization/topic strategy.

---

# 16. KAFKA — Failure Classification

Current error handling broadly follows:

```text
processing failure
       │
       ▼
retry
       │
       ▼
retry
       │
       ▼
retry
       │
       ▼
DLT
```

However, not all failures should be treated identically.

## Transient examples

```text
database temporarily unavailable
temporary network problem
temporary dependency outage
```

These may succeed later.

## Non-Recoverable examples

```text
invalid payload
unsupported event format
deserialization failure
permanently invalid data
```

Repeated immediate retry may provide little value.

## Phase 1 Action

Define explicit categories:

```text
TRANSIENT
    ↓
retry/recovery

NON-RECOVERABLE
    ↓
DLT

BUSINESS FAILURE
    ↓
business workflow/event
```

These concepts must remain separate.

---

# 17. KAFKA — Service Outage Behaviour

If Order Service itself is unavailable:

```text
Payment / Inventory
       │
       ▼
Kafka
       │
       ▼
topic partition
       │
       X
Order Service DOWN
```

messages can remain in Kafka according to topic retention and capacity configuration.

When Order Service returns, its consumer group can continue from committed progress.

Therefore a temporary consumer outage does **not** inherently mean:

```text
everything → DLT
```

## Operational Improvement

Eventually monitor:

```text
consumer lag
retry activity
DLT volume
```

to distinguish:

```text
consumer temporarily behind
```

from:

```text
records repeatedly failing processing
```

---

# 18. CONFIGURATION — Multiple Kafka Configuration Sources

Kafka behaviour is configured across multiple locations, including explicit Java configuration and application configuration.

This increases the risk of:

```text
duplicated properties
conflicting defaults
environment-specific surprises
copy/paste mistakes
```

## Phase 1 Action

Establish clear configuration ownership for:

```text
bootstrap servers
consumer group
serializers
deserializers
producer reliability
listener concurrency
retry/error handling
```

Prefer Spring Boot configuration where appropriate and retain explicit beans where custom behaviour is actually required.

---

# 19. CONFIGURATION — Kafka Bean Naming

Order Service currently contains Kafka bean names resembling:

```text
inventoryProducerFactory
inventoryKafkaTemplate
```

inside Order Service configuration.

These appear to be copy/paste residue.

## Phase 1 Cleanup

Rename beans consistently according to their actual responsibility.

---

# 20. OBJECT MAPPER / SHARED EVENTS

Current architecture contains:

```text
shared-events
     │
     ▼
EventObjectMapperFactory
```

and each service contains a small Spring:

```text
ObjectMapperConfig
```

Do **not** move Spring `@Configuration` classes into `shared-events` merely to remove several lines of duplicated configuration.

`shared-events` should remain framework-light.

## Phase 1 Investigation

Determine whether:

```text
EventObjectMapperFactory
```

is actually necessary.

Spring Boot already manages an application `ObjectMapper`.

The eventual solution may therefore be:

```text
use/configure Spring Boot ObjectMapper
```

rather than:

```text
move Spring configuration into shared-events
```

Resolve the Kafka serialization strategy before making this change.

---

# 21. EVENT CONTRACT — Version Constants

Some listeners currently contain:

```java
next(event.metadata(), "order-service", 1)
```

The literal:

```text
1
```

represents an event contract version.

Scattering these literals makes version ownership harder to understand.

`OrderCreatedEvent` already demonstrates a clearer pattern with:

```java
public static final int VERSION = 1;
```

## Phase 1 Action

Review whether each versioned event contract should explicitly own its version constant.

---

# 22. EVENT CONTRACT — Unused Requested Events

During the `shared-events` audit, the following appeared unused:

```text
PaymentRequestedEvent
InventoryReserveRequestedEvent
```

The currently audited Order workflow publishes:

```text
OrderCreatedEvent
```

and Payment reacts directly to it.

## Phase 1 Cleanup

Verify repository-wide usage.

If genuinely unused and not part of the intended architecture, remove them rather than retaining dead event contracts.

---

# 23. DLT Constants

`shared-events` currently contains explicit constants such as:

```text
ORDER_EVENTS_V1_DLT
PAYMENT_EVENTS_V1_DLT
INVENTORY_EVENTS_V1_DLT
```

while the actual `DeadLetterPublishingRecoverer` derives DLT names dynamically:

```java
record.topic() + ".DLT"
```

This explains why IntelliJ currently shows no usages for those constants.

## Phase 1 Cleanup

Choose one clear source of truth.

Either explicit DLT constants provide useful contract/documentation value, or the naming convention generates them dynamically.

Avoid maintaining two representations without a reason.

---

# 24. OBSERVABILITY IMPROVEMENTS

Current observability direction should remain.

Eventually add/monitor business and Kafka-specific signals such as:

```text
orders created
orders completed
orders cancelled

Kafka consumer lag
Kafka processing failures
retry count
DLT records
outbox backlog
outbox publication failures
```

This will make the local Docker environment behave more like an operational distributed system rather than simply a collection of running containers.

---

# 25. PHASE 1 PRIORITY ORDER

The Order Service should not be refactored randomly.

Use the following sequence.

## Priority 1 — Consumer Idempotency

Fix:

```text
ProcessedEvent
+
Order mutation
```

transaction semantics.

Address concurrent duplicate delivery.

---

## Priority 2 — Transactional Outbox

Protect:

```text
Order DB state
+
outgoing Kafka event
```

from dual-write inconsistency.

Cover:

```text
Order creation
Order completion
Order cancellation/failure
```

---

## Priority 3 — Kafka Retry Ownership

Make Spring Kafka the primary owner of Kafka record-processing recovery.

Remove or justify overlapping:

```text
@Retry
@CircuitBreaker
@Bulkhead
fallback
```

---

## Priority 4 — Failure Classification

Separate:

```text
technical/transient failure

non-recoverable event failure

business failure
```

and define appropriate recovery/DLT behaviour.

---

## Priority 5 — Event Serialization / Routing

Resolve:

```text
JsonDeserializer<Object>
type headers
shared topics
concrete event classes
listener routing
```

as one design problem.

---

## Priority 6 — Order State Machine

Define and enforce legal Order transitions.

---

## Priority 7 — Concurrent Order Updates

Evaluate optimistic locking / JPA `@Version` together with state-transition enforcement.

---

## Priority 8 — API Validation

Protect:

```text
POST /api/orders
```

from invalid input.

---

## Priority 9 — Price Trust Boundary

Prevent the client from becoming the authoritative source of the amount eventually charged.

---

## Priority 10 — Configuration Cleanup

Clean up:

```text
Kafka configuration duplication
Kafka bean naming
ObjectMapper strategy
event VERSION literals
unused event contracts
DLT constants
```

---

# 26. DO NOT ADD WITHOUT A CLEAR REQUIREMENT

During Phase 1, avoid adding technologies merely because they appear in enterprise systems.

Do not automatically add:

- more Resilience4j annotations
- pessimistic locking
- additional microservices
- a shared infrastructure/common module
- complicated Order statuses
- complicated Saga frameworks
- extra Kafka topics without a routing requirement
- new abstraction layers merely to reduce a few lines of duplication

The objective is:

```text
correctness
+
maintainability
+
operability
```

not:

```text
maximum number of technologies
```

---

# 27. KEY ARCHITECTURAL LESSON FROM ORDER SERVICE

The Order audit identified four different distributed-system correctness problems.

They must not be confused.

## Problem A — Duplicate Event

```text
same eventId
delivered multiple times
       │
       ▼
IDEMPOTENCY
```

## Problem B — Concurrent Aggregate Modification

```text
different eventIds
same orderId
processed concurrently
       │
       ▼
CONCURRENCY CONTROL
```

## Problem C — Invalid State Transition

```text
Order currently COMPLETED

new operation wants CANCELLED
       │
       ▼
DOMAIN INVARIANT / STATE MACHINE
```

## Problem D — Database/Event Inconsistency

```text
DB commit succeeds

Kafka publish fails
       │
       ▼
TRANSACTIONAL OUTBOX
```

Therefore:

```text
Idempotency
        ≠
Concurrency control
        ≠
Domain state validation
        ≠
Outbox
```

Each mechanism solves a different problem.

---

# 28. Overall Phase 0 Conclusion

The Order Service has a sound high-level boundary:

```text
Client
   │
   ▼
API Gateway
   │
   ▼
Order Service
   │
   ├────► orders_db
   │
   ▼
Kafka
   │
   ├────► Payment Service
   │
   └────► Inventory Service
```

The service already demonstrates useful distributed-system concepts:

```text
database-per-service

Kafka asynchronous communication

event-driven workflow

event metadata

correlation IDs

idempotency awareness

retry/DLT infrastructure

service discovery

observability
```

The primary weakness is **not a lack of technologies**.

The important gaps concern the correctness of interactions between the technologies already present:

```text
transaction boundaries

idempotency semantics

DB/Kafka consistency

concurrent aggregate updates

domain invariants

event serialization/routing

failure classification

recovery procedures
```

Therefore Phase 1 should strengthen the existing architecture rather than replace it.

The progression should be:

```text
CURRENT

DB + Kafka + retry + DLT + idempotency
already exist

              │
              ▼

PHASE 1

make their interaction correct and explicit

              │
              ▼

TARGET

reliable event-driven Order Service

with explicit:

• consistency guarantees
• idempotency semantics
• concurrency handling
• state transitions
• retry ownership
• failure classification
• recovery procedures
• observability
```

---
