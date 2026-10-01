# Payment Service — Architecture Audit

**Service:** `payment-service`  
**Phase:** Phase 0 — Architecture Audit  
**Status:** Complete  
**Purpose:** Record the current architecture, confirmed issues, improvement opportunities, and Phase 1 actions without implementing the fixes during the audit.

---

# 1. Current Responsibility

The Payment Service is an internal Kafka-driven service responsible for reacting to order creation and simulating payment processing.

Unlike Order Service, Payment Service currently exposes no public business REST endpoint.

## Consumes

```text
order.events.v1
└── OrderCreatedEvent
```

## Processes

Currently:

```text
OrderCreatedEvent
       │
       ▼
PaymentEventListener
       │
       ▼
PaymentService.processPayment()
       │
       ▼
PaymentService.charge()
       │
       ▼
simulated payment
```

## Produces

```text
payment.events.v1

├── PaymentCompletedEvent
└── PaymentFailedEvent
```

## Persists

Currently:

```text
payments_db

└── processed_events
```

using:

```text
ProcessedEvent
ProcessedEventRepository
```

## Does NOT Currently Persist

There is currently no:

```text
Payment
PaymentStatus
PaymentRepository
```

Therefore `payments_db` does not currently store actual payment business state.

## Simplified Current Flow

```text
Order Service
     │
     ▼
OrderCreatedEvent
     │
     ▼
Kafka
     │
     ▼
PaymentEventListener
     │
     ▼
IdempotencyGuard
     │
     ▼
PaymentService
     │
     ▼
simulated charge
     │
     ▼
PaymentCompletedEvent
     │
     ▼
Kafka
```

On processing failure, the current Resilience4j fallback can instead produce:

```text
PaymentFailedEvent
```

This behaviour requires redesign because technical processing failure and genuine payment failure are not equivalent.

---

# 2. KEEP — Existing Good Decisions

## Service Boundary

- Payment Service is an internal service rather than unnecessarily exposing a public REST API.
- Payment reacts asynchronously to Order events.
- Communication with Order Service is decoupled through Kafka.
- Payment has its own `payments_db`.
- Payment does not directly modify Order state.
- Payment communicates outcomes through events.

## Event Architecture

- Payment consumes `OrderCreatedEvent`.
- Payment publishes explicit result events:
  - `PaymentCompletedEvent`
  - `PaymentFailedEvent`
- Kafka records use the aggregate/order ID as their key.
- Event metadata is propagated.
- `correlationId` continues across the distributed workflow.
- `PaymentEventPublisher` encapsulates `KafkaTemplate`.

## Monetary Handling

The service uses:

```java
BigDecimal
```

rather than floating-point types for monetary calculations.

Current amount calculation:

```java
BigDecimal amount =
        price.multiply(BigDecimal.valueOf(quantity));
```

is directionally appropriate for monetary arithmetic.

## Basic Validation

Current code already rejects:

```text
quantity <= 0
```

and:

```text
amount <= 0
```

These checks should remain conceptually, although their eventual ownership may change during the domain refactor.

## Processed Event Infrastructure

Keep the concept of:

```text
ProcessedEvent
```

with:

```text
eventId
processedAt
```

The primary key on `eventId` provides a database-level uniqueness mechanism.

`ProcessedEvent` is also more deliberately encapsulated than the current Order entity because it uses:

```java
@Getter
```

rather than broad Lombok `@Data`.

## Kafka Failure Handling Foundation

Keep the foundation provided by:

```text
ErrorHandlingDeserializer
DefaultErrorHandler
DeadLetterPublishingRecoverer
```

These are appropriate Kafka-oriented mechanisms.

Also keep conceptually:

- producer `acks=all`
- consumer auto commit disabled
- retry/backoff infrastructure
- DLT capability

## Observability

Keep:

- event ID logging
- correlation ID logging
- Order ID logging
- MDC cleanup
- Actuator
- Prometheus
- health probes
- distributed tracing
- Zipkin

The observability direction is one of the stronger production-oriented parts of the service.

---

# 3. HIGH PRIORITY — Missing Payment Domain

This is the largest Payment-specific architectural gap discovered during Phase 0.

Currently there is no:

```text
Payment.java
PaymentStatus.java
PaymentRepository.java
```

The database therefore answers:

```text
"Which Kafka event IDs have been processed?"
```

but cannot properly answer:

```text
"What happened to the payment for Order 123?"
```

After a successful call to:

```java
paymentService.processPayment(...)
```

the service publishes:

```text
PaymentCompletedEvent
```

but no corresponding Payment business record is persisted.

## Current State

```text
payments_db

processed_events
    └── eventId=A
```

## Missing Business State

Conceptually something like:

```text
paymentId
orderId
amount
status
transactionId
createdAt
updatedAt
```

does not currently exist.

## Phase 1 Action

Introduce a deliberately small Payment aggregate.

Do not attempt to model a complete banking/payment platform.

A reasonable initial lifecycle to investigate is conceptually:

```text
PENDING
COMPLETED
FAILED
```

The exact states should be decided during Phase 1 based on the workflow rather than added merely for realism.

---

# 4. HIGH PRIORITY — Missing Payment Business Invariant

Current idempotency is based on:

```text
eventId
```

Suppose Payment receives:

```text
OrderCreatedEvent
eventId=A
orderId=123
```

and later another logically duplicate event:

```text
OrderCreatedEvent
eventId=B
orderId=123
```

The current idempotency mechanism sees:

```text
A ≠ B
```

and therefore both are considered new events.

Potential result:

```text
Order 123
   │
   ▼
charge()

Order 123
   │
   ▼
charge()
```

Event idempotency therefore does not automatically enforce:

```text
one logical successful payment per order
```

## Important Distinction

```text
EVENT IDEMPOTENCY

"Have I already processed event A?"
```

is different from:

```text
BUSINESS INVARIANT

"Has Order 123 already been successfully paid?"
```

## Phase 1 Action

Define and enforce the Payment business invariant around `orderId`.

This likely belongs in the Payment domain/database model rather than being solved only through Kafka event IDs.

---

# 5. HIGH PRIORITY — Idempotency Is Marked Too Early

Current listener performs:

```java
if (idempotencyGuard.alreadyProcessed(eventId)) {
    return;
}

paymentService.processPayment(...);
```

`alreadyProcessed()` currently does:

```text
existsById(eventId)
       │
       ▼
if absent
       │
       ▼
INSERT ProcessedEvent
       │
       ▼
return false
```

Therefore the event is recorded before payment processing succeeds.

Possible failure:

```text
ProcessedEvent INSERT       ✓

paymentService.processPayment()
                            ✗
```

Now the database says:

```text
event A processed
```

although the payment operation did not successfully complete.

When Kafka delivers the event again:

```text
alreadyProcessed(A)
       │
       ▼
true
       │
       ▼
return
```

The payment may never be attempted again.

## Phase 1 Action

Redesign the idempotency transaction boundary.

`ProcessedEvent` should represent a successfully handled event according to the defined transaction semantics, not merely an event whose processing began.

---

# 6. HIGH PRIORITY — Idempotency Race Condition

Payment uses the same check-then-act pattern identified in Order:

```text
existsById(eventId)
       │
       ▼
save(eventId)
```

Two concurrent executions could theoretically observe:

```text
Thread A                   Thread B

exists? false              exists? false

continue                    continue
```

For Payment this is particularly important because the protected operation may eventually become an external financial side effect.

## Phase 1 Action

Use database uniqueness deliberately as part of the idempotency design rather than relying solely on:

```text
SELECT → INSERT
```

logic.

---

# 7. HIGH PRIORITY — External Payment Side Effects Require Stronger Idempotency

Today:

```java
charge(...)
```

only performs:

```java
System.out.printf(...)
```

so no real money moves.

Eventually, if a real provider were integrated:

```text
Payment Service
      │
      ▼
Payment Provider
      │
      ▼
charge customer
```

a dangerous scenario becomes possible:

```text
provider charge succeeds
        │
        ▼
service crashes
        │
        ▼
local transaction never records completion
        │
        ▼
Kafka retries
        │
        ▼
provider charged again
```

Database event idempotency alone cannot completely solve an external side effect that occurred before the local service knew its final outcome.

## Phase 1 Design Requirement

Understand the difference between:

```text
consumer event idempotency
```

and:

```text
external provider idempotency
```

Future payment-provider integration should use provider-side idempotency mechanisms where supported.

No real provider needs to be integrated during the initial Phase 1 refactor.

---

# 8. HIGH PRIORITY — Technical Failure Is Incorrectly Converted Into Payment Failure

Current listener has:

```java
@CircuitBreaker(
    name = "payment-kafka",
    fallbackMethod = "fallback"
)
```

and the fallback publishes:

```text
PaymentFailedEvent
```

This means:

```text
technical exception
       │
       ▼
fallback()
       │
       ▼
PaymentFailedEvent
```

But these concepts are not equivalent.

For example:

```text
MySQL unavailable
```

does not necessarily mean:

```text
customer's payment was declined
```

Likewise:

```text
thread rejected
network unavailable
temporary infrastructure problem
```

does not necessarily mean:

```text
PAYMENT FAILED
```

## Phase 1 Action

Separate:

```text
BUSINESS PAYMENT FAILURE

example:
payment declined
invalid payment
provider confirms failure
```

from:

```text
TECHNICAL PROCESSING FAILURE

example:
database outage
network timeout
temporary infrastructure failure
```

Technical failures should generally participate in retry/recovery rather than immediately becoming business `PaymentFailedEvent`s.

---

# 9. HIGH PRIORITY — Ambiguous Payment Outcome

Payment systems have another important category:

```text
UNKNOWN / AMBIGUOUS OUTCOME
```

Example:

```text
Payment Service
      │
      ▼
provider receives request
      │
      ▼
provider processes charge
      │
      X
network timeout before response
```

The Payment Service cannot safely conclude:

```text
SUCCESS
```

or:

```text
FAILED
```

without further information.

## Phase 1 Learning/Design Requirement

Keep three concepts separate:

```text
SUCCESS

BUSINESS FAILURE

UNKNOWN / TECHNICAL OUTCOME
```

A future real-provider design would normally require reconciliation or provider status lookup for ambiguous outcomes.

Do not implement unnecessary provider complexity now, but preserve the architectural distinction.

---

# 10. HIGH PRIORITY — Overlapping Retry Mechanisms

Payment listener currently uses:

```java
@Retry(name = "payment-kafka")
```

while Kafka configuration also provides:

```java
DefaultErrorHandler
```

with:

```java
new FixedBackOff(1000L, 3)
```

Therefore:

```text
Resilience4j Retry
       +
Spring Kafka retry/recovery
```

can overlap.

This makes the number of attempts and failure behaviour harder to reason about.

For Payment, duplicate execution deserves particular care because processing may eventually involve an external financial operation.

## Phase 1 Direction

Spring Kafka should primarily own Kafka record-processing retry/recovery.

Only retain another retry mechanism when it protects a clearly different dependency or operation.

---

# 11. HIGH PRIORITY — Fallback Can Hide Failure From Spring Kafka

Current processing can conceptually become:

```text
Kafka Listener
      │
      ▼
exception
      │
      ▼
Resilience4j
      │
      ▼
fallback()
      │
      ▼
PaymentFailedEvent
      │
      ▼
return normally
```

Spring Kafka may therefore not receive the original processing exception in the way required for:

```text
DefaultErrorHandler
       │
       ▼
retry
       │
       ▼
DLT
```

## Phase 1 Action

Remove/reconsider generic fallback behaviour around the Kafka listener.

Kafka processing failures intended for retry/recovery must reach Spring Kafka.

---

# 12. PAYMENT DOMAIN — Transaction ID Ownership

Current listener creates:

```java
UUID.randomUUID().toString()
```

when constructing:

```text
PaymentCompletedEvent
```

Therefore `transactionId` is currently generated by the Kafka listener rather than the Payment domain/service/provider.

Conceptually:

```text
PaymentEventListener
       │
       ├── processes payment
       │
       ├── decides transaction ID
       │
       └── constructs result event
```

This places too much payment-specific orchestration in the Kafka adapter.

## Phase 1 Action

Decide where transaction identity belongs.

For the simulated implementation it may be generated by the Payment application/domain layer.

For a real provider it would likely be associated with the provider transaction/reference.

---

# 13. PAYMENT DOMAIN — Listener Contains Too Much Orchestration

Current listener performs several responsibilities:

```text
receive Kafka event
       │
       ├── idempotency
       ├── logging context
       ├── payment orchestration
       ├── transaction ID generation
       ├── event construction
       └── failure conversion
```

The Kafka listener should increasingly behave as an adapter.

Target direction:

```text
Kafka
  │
  ▼
PaymentEventListener
  │
  ▼
Payment application/service layer
  │
  ├── domain rules
  ├── persistence
  └── payment processing
```

## Phase 1 Action

Move business/use-case orchestration away from the Kafka adapter where appropriate.

Do not create unnecessary layers purely to imitate Clean Architecture.

---

# 14. PAYMENT DOMAIN — `userId` Is Currently Discarded

`OrderCreatedEvent` contains:

```text
userId
```

but current Payment processing eventually performs:

```java
charge(orderId, null, amount);
```

Therefore:

```text
OrderCreatedEvent.userId
       │
       X
PaymentService
```

The service explicitly passes:

```text
null
```

for the user.

## Phase 1 Action

Decide whether Payment actually needs `userId`.

If it does:

```text
propagate/use it deliberately
```

If it does not:

```text
do not carry meaningless parameters through PaymentService
```

Avoid placeholder `null` domain arguments.

---

# 15. PRICE TRUST BOUNDARY

Payment calculates:

```java
amount = price × quantity
```

from values originating in:

```text
CreateOrderRequest
```

Therefore the current trust chain is:

```text
Client
  │
  ▼
Order price
  │
  ▼
OrderCreatedEvent
  │
  ▼
Payment
  │
  ▼
amount charged
```

Payment currently has no authoritative way to determine whether the supplied price is legitimate.

## Phase 1 Design Requirement

Coordinate this finding with the Order Service audit.

The eventual architecture should not treat an untrusted client-supplied price as authoritative.

Do not automatically create another service simply to solve this.

---

# 16. DATABASE / EVENT CONSISTENCY

Once Payment begins persisting a real Payment aggregate, another distributed consistency problem appears.

Example:

```text
Payment = COMPLETED
       │
       ▼
payments_db commit
       │
       ▼
publish PaymentCompletedEvent
```

Possible failure:

```text
Payment DB update          ✓
Kafka publication          ✗
```

Now:

```text
payments_db
Payment = COMPLETED
```

while:

```text
Order Service
never receives PaymentCompletedEvent
```

## Phase 1 Action

Payment should participate in the same Transactional Outbox investigation as Order Service.

Conceptually:

```text
DB transaction
     │
     ├── Payment state
     └── OutboxEvent
             │
             ▼
          COMMIT
```

followed by reliable Kafka publication.

---

# 17. KAFKA — Configuration Has Multiple Sources of Truth

Kafka configuration currently exists in:

```text
application.yml

KafkaConsumerConfig

KafkaProducerConfig

@KafkaListener
```

This already produces inconsistent-looking configuration.

For example:

```text
application.yml
group-id = payment-service
```

while Java consumer configuration contains:

```text
payment-service-group
```

and the listener explicitly uses:

```text
groupId = payment-service
```

Understanding effective behaviour therefore requires inspecting multiple locations.

## Phase 1 Action

Consolidate Kafka configuration ownership.

Prefer Spring Boot properties where appropriate.

Keep explicit Java configuration for genuinely custom behaviour such as:

```text
DefaultErrorHandler
DeadLetterPublishingRecoverer
```

when needed.

---

# 18. CONFIGURATION — Environment Defaults Conflict

Java Kafka configuration defaults to:

```text
localhost:9092
```

while YAML defaults to:

```text
kafka:9092
```

These represent different runtime environments.

```text
localhost:9092
```

normally means:

```text
application running on host
```

while:

```text
kafka:9092
```

means:

```text
application running inside Docker network
```

## Phase 1 Action

Introduce a clearer environment/profile strategy.

Conceptually:

```text
application.yml
       │
       └── common configuration

application-local.yml
       │
       └── localhost infrastructure

application-docker.yml
       │
       └── Docker service names

future production configuration
       │
       └── environment/secrets/platform config
```

The exact structure should be designed during the configuration audit/refactor.

---

# 19. KAFKA — Event Serialization / Type Routing

Producer explicitly sets:

```java
JsonSerializer.ADD_TYPE_INFO_HEADERS = false
```

while consumer uses:

```java
JsonDeserializer<Object>
```

At the same time:

```text
order.events.v1
```

can contain several concrete event types:

```text
OrderCreatedEvent
OrderCompletedEvent
OrderFailedEvent
```

Payment only wants:

```text
OrderCreatedEvent
```

This raises two connected questions:

```text
How is the concrete event type reconstructed?
```

and:

```text
How does Payment ignore Order events it does not care about?
```

## Phase 1 Action

Resolve serialization and event routing together.

Do not simply enable/disable serializer options without defining the event contract strategy.

---

# 20. KAFKA — Topic Subscription Is Broader Than Payment's Business Interest

Payment subscribes to:

```text
order.events.v1
```

but the audited listener only handles:

```text
OrderCreatedEvent
```

The topic also conceptually carries:

```text
OrderCompletedEvent
OrderFailedEvent
```

Payment has no shown business need for those events.

## Phase 1 Investigation

Verify what actually happens when those event types reach the Payment consumer group.

Then decide the intended routing strategy.

Possible strategies should be evaluated based on requirements rather than selected merely for architectural fashion.

---

# 21. KAFKA — Trusted Packages Are Too Broad

Consumer currently contains:

```java
jsonDeserializer.addTrustedPackages("*");
```

and configuration also contains:

```yaml
spring.json.trusted.packages: "*"
```

This is both duplicated and broader than necessary.

## Phase 1 Hardening

Restrict trusted deserialization packages to the event contracts the application actually owns/trusts.

This is not the highest-risk issue in the project but is appropriate hardening.

---

# 22. KAFKA — DLT Behaviour

Current recoverer generates:

```java
record.topic() + ".DLT"
```

Therefore:

```text
order.events.v1
```

failures handled by the Payment consumer would be sent to:

```text
order.events.v1.DLT
```

This raises an important ownership question.

A DLT generated because **Payment failed to process an Order event** may conceptually need consumer-specific ownership rather than appearing to represent a global Order topic failure.

## Phase 1 Investigation

Define DLT ownership and naming.

Questions to answer:

```text
Who owns the failed record?

Which consumer failed?

Can another consumer successfully process the same source event?

How will the DLT be replayed?

How do operators know which service failed?
```

This should be resolved as part of the Kafka recovery strategy.

---

# 23. KAFKA — Async Send Logging

Publisher currently performs:

```java
kafkaTemplate.send(...);

log.info("Payment event {} published ...");
```

`KafkaTemplate.send()` is asynchronous.

Therefore the log statement immediately following `send()` does not necessarily mean Kafka has acknowledged successful publication.

## Phase 1 Improvement

Ensure logs/metrics distinguish between:

```text
publication requested
```

and:

```text
broker acknowledged publication
```

This becomes even more important once Outbox is introduced.

---

# 24. RESILIENCE — Bulkhead Requires Re-Evaluation

Kafka listener factory already configures:

```text
concurrency = 3
```

while Resilience4j contains:

```text
bulkhead
max-concurrent-calls = 5
```

There is also:

```text
thread-pool-bulkhead
```

configuration.

However, the listener currently uses:

```java
@Bulkhead(name = "payment-kafka")
```

without explicitly selecting the thread-pool type.

Therefore the configured thread-pool bulkhead appears unused based on the code audited so far.

## Phase 1 Action

Do not stack multiple concurrency controls without assigning each one a specific responsibility.

Review:

```text
Kafka partitions
Kafka listener concurrency
Resilience4j semaphore bulkhead
Resilience4j thread-pool bulkhead
```

and remove mechanisms that do not solve a demonstrated problem.

---

# 25. OBJECT MAPPER / SHARED EVENTS

Payment contains:

```java
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return EventObjectMapperFactory.get();
    }
}
```

This is duplicated across services.

However, moving the Spring configuration into:

```text
shared-events
```

is not automatically the right solution.

`shared-events` should remain framework-light.

## Phase 1 Investigation

Determine whether:

```text
EventObjectMapperFactory
```

is needed at all.

Spring Boot already provides and manages an `ObjectMapper`.

The eventual solution may be to use/configure Boot's mapper rather than centralizing Spring configuration in `shared-events`.

Resolve Kafka serialization strategy first.

---

# 26. DATABASE — Schema Management

Current configuration uses:

```text
ddl-auto: update
```

This is convenient for the current local development environment.

It should not become the eventual production-like schema-management strategy.

Payment will likely require schema evolution when adding:

```text
payments

processed_events changes

outbox_events

indexes

unique constraints
```

## Phase 1/2 Improvement

Introduce explicit database migrations using something such as:

```text
Flyway
```

or:

```text
Liquibase
```

Choose one rather than adding both.

---

# 27. CONFIGURATION — Database Credentials

Current defaults include:

```text
username = admin
password = admin
```

These are acceptable as intentionally local Docker development defaults.

They should not become production credentials.

## Later Deployment Requirement

Externalize real secrets through the deployment environment/secrets mechanism.

---

# 28. OBSERVABILITY — Tracing

Current configuration uses:

```text
sampling probability = 1.0
```

For this local learning environment, 100% tracing is useful because it allows complete inspection of distributed flows.

For a high-volume production system, sampling would normally become environment-specific.

## Keep For Local Development

```text
100% tracing
```

is reasonable for the current project stage.

---

# 29. EVENT CONTRACT — Magic Version Literal

Payment currently creates events using:

```java
next(event.metadata(), "payment-service", 1)
```

The version:

```text
1
```

is a contract value.

## Phase 1 Improvement

Review explicit version ownership for event contracts rather than scattering magic literals through listeners.

Conceptually:

```java
PaymentCompletedEvent.VERSION
PaymentFailedEvent.VERSION
```

if that model is selected.

---

# 30. EVENT CONTRACT — `PaymentRequestedEvent`

`shared-events` contains:

```text
PaymentRequestedEvent
```

but the audited workflow is:

```text
OrderCreatedEvent
       │
       ▼
Payment Service
```

No current use of:

```text
PaymentRequestedEvent
```

has been found.

## Phase 1 Cleanup

Verify repository-wide usage.

If genuinely unused and not part of the intended architecture, remove it rather than keeping a dead event contract.

---

# 31. CLEANUP — `System.out.printf`

Current simulated charge uses:

```java
System.out.printf(...)
```

The project already uses structured logging.

## Phase 1 Cleanup

Replace this with the project's logging infrastructure.

This is a minor cleanup rather than an architectural priority.

---

# 32. PAYMENT FAILURE CLASSIFICATION

Payment requires a more precise failure model than a generic service.

During Phase 1, distinguish at least conceptually between:

## Successful Payment

```text
provider/payment operation confirmed
       │
       ▼
Payment = COMPLETED
       │
       ▼
PaymentCompletedEvent
```

## Business Failure

```text
payment explicitly rejected
       │
       ▼
Payment = FAILED
       │
       ▼
PaymentFailedEvent
```

## Technical Failure

```text
database unavailable
network unavailable
temporary infrastructure issue
       │
       ▼
retry/recovery
```

## Ambiguous Outcome

```text
request may have reached provider
response lost/unknown
       │
       ▼
reconciliation required
```

Do not collapse these four states into one generic exception path.

---

# 33. PHASE 1 PRIORITY ORDER

Payment Service should be refactored in a deliberate sequence.

## Priority 1 — Design Minimal Payment Aggregate

Introduce:

```text
Payment
PaymentStatus
PaymentRepository
```

with only the state genuinely required by the workflow.

---

## Priority 2 — Define Payment Business Invariant

Determine and enforce what should happen when multiple events reference the same:

```text
orderId
```

Protect against duplicate logical payment.

---

## Priority 3 — Redesign Consumer Idempotency

Correct:

```text
ProcessedEvent
+
Payment processing/persistence
```

transaction semantics.

Address concurrent duplicate delivery.

---

## Priority 4 — Separate Business Failure From Technical Failure

Stop converting arbitrary technical exceptions into:

```text
PaymentFailedEvent
```

---

## Priority 5 — Simplify Kafka Retry Ownership

Make Spring Kafka the primary owner of Kafka listener record-processing recovery.

Remove or justify overlapping:

```text
@Retry
@CircuitBreaker
@Bulkhead
fallback
```

---

## Priority 6 — Move Payment Orchestration Out of Listener

Reduce the Kafka listener toward an adapter role.

Move payment-specific use-case decisions into the appropriate application/domain layer.

---

## Priority 7 — Resolve Event Serialization / Routing

Address together:

```text
JsonDeserializer<Object>
type headers
shared order topic
concrete event types
listener routing
```

---

## Priority 8 — Introduce Transactional Outbox

Once Payment state is persisted, protect:

```text
Payment DB state
+
PaymentCompletedEvent / PaymentFailedEvent
```

against dual-write inconsistency.

---

## Priority 9 — Consolidate Configuration

Clean:

```text
Kafka Java/YAML duplication
consumer group inconsistencies
environment defaults
unused bulkhead configuration
ObjectMapper strategy
```

---

## Priority 10 — Introduce Schema Migrations

Move toward explicit database schema evolution with:

```text
Flyway OR Liquibase
```

---

## Priority 11 — Improve Tests

Add tests around:

```text
duplicate eventId

different eventIds for same orderId

payment business invariant

payment state transitions

processing failure before commit

processing failure after external operation

Kafka retry

DLT recovery

Outbox publication

concurrent duplicate delivery
```

---

# 34. DO NOT ADD WITHOUT A CLEAR REQUIREMENT

During Phase 1, do not automatically introduce:

- Stripe
- PayPal
- complex payment gateways
- refund workflows
- chargebacks
- disputes
- settlement systems
- many Payment statuses
- another retry framework
- another message broker
- distributed transactions / 2PC
- extra microservices
- complex Saga frameworks
- generic shared infrastructure modules

The objective is to demonstrate correct distributed-system engineering, not reproduce an entire payment platform.

---

# 35. KEY ARCHITECTURAL LESSON FROM PAYMENT SERVICE

Payment exposes several distinct reliability problems.

They require different solutions.

## Problem A — Duplicate Kafka Delivery

```text
same eventId
delivered again
       │
       ▼
EVENT IDEMPOTENCY
```

## Problem B — Different Events For Same Order

```text
eventId=A
orderId=123

eventId=B
orderId=123
       │
       ▼
PAYMENT BUSINESS INVARIANT
```

## Problem C — Database/Event Inconsistency

```text
Payment DB commit ✓
Kafka publication ✗
       │
       ▼
TRANSACTIONAL OUTBOX
```

## Problem D — External Charge Repeated

```text
provider charge ✓
service crashes
Kafka retries
provider charge again
       │
       ▼
PROVIDER IDEMPOTENCY
```

## Problem E — Unknown External Outcome

```text
provider may have charged
response lost
       │
       ▼
RECONCILIATION
```

## Problem F — Temporary Infrastructure Failure

```text
DB/network temporarily unavailable
       │
       ▼
RETRY / RECOVERY
```

Therefore:

```text
Event idempotency
       ≠
Payment business invariant
       ≠
Transactional Outbox
       ≠
Provider idempotency
       ≠
Reconciliation
       ≠
Retry
```

Each solves a different failure mode.

---

# 36. Comparison With Order Service

The two services should share engineering principles without being artificially identical.

```text
ORDER SERVICE                 PAYMENT SERVICE

Order                         Payment
OrderStatus                   PaymentStatus

OrderRepository               PaymentRepository
✓ exists                      ✗ missing

ProcessedEvent                ProcessedEvent
✓ exists                      ✓ exists

ProcessedEventRepository      ProcessedEventRepository
✓ exists                      ✓ exists

Business state persisted      Business state persisted
✓ yes                         ✗ no

Event idempotency             Event idempotency
△ flawed boundary             △ flawed boundary

Outbox                        Outbox
✗ missing                     ✗ missing

Kafka retry ownership         Kafka retry ownership
△ overlapping                 △ overlapping
```

The objective is therefore not:

```text
make Payment identical to Order
```

but:

```text
apply the same engineering discipline
while respecting different domain responsibilities
```

---

# 37. Overall Phase 0 Conclusion

The Payment Service has a valid high-level service boundary:

```text
Order Service
     │
     ▼
Kafka
     │
     ▼
Payment Service
     │
     ▼
Kafka
     │
     ▼
Order / downstream workflow
```

The service already demonstrates useful concepts:

```text
Kafka-driven communication

service isolation

database-per-service direction

event metadata

correlation IDs

idempotency awareness

Kafka retry/DLT infrastructure

observability

BigDecimal monetary arithmetic
```

The primary problem is that Payment currently behaves more like:

```text
Kafka event processor
+
simulated charge
```

than:

```text
state-owning Payment microservice
```

The most important gaps concern:

```text
missing Payment domain state

business-level payment uniqueness

incorrect idempotency transaction boundary

technical vs business failure classification

overlapping retry mechanisms

event serialization/routing

DB/Kafka consistency

future external-side-effect semantics
```

Therefore Phase 1 should **strengthen the existing Payment Service boundary rather than replace it**.

The progression should be:

```text
CURRENT

OrderCreatedEvent
      │
      ▼
Payment listener
      │
      ▼
simulated charge
      │
      ▼
PaymentCompletedEvent

No persisted Payment
```

then:

```text
              │
              ▼

PHASE 1

introduce Payment domain

correct idempotency

define business invariant

separate failure types

simplify Kafka resilience

introduce reliable event publication
```

leading toward:

```text
              │
              ▼

TARGET

Reliable Payment Service

with explicit:

• Payment state
• payment lifecycle
• business invariants
• event idempotency
• transaction boundaries
• Kafka recovery ownership
• DB/Kafka consistency
• external-provider boundary
• observability
```

---