# Notification Service --- Architecture Audit

**Project:** Kafka Microservices Distributed System\
**Service:** `notification-service`\
**Phase:** Phase 0 --- Architecture Audit\
**Audit date:** 03 October 2026\
**Purpose:** Capture the current state, architectural findings, risks,
and Phase 1 refactoring targets before implementation work begins.

------------------------------------------------------------------------

## 1. Audit Scope

The Notification Service is intentionally less mature than Order,
Payment, and Inventory. Its current purpose is primarily
proof-of-concept notification output through the console.

The Phase 1 target should therefore **not** be an over-engineered
enterprise notification platform. The goal is to turn the current proof
of concept into a small, credible, well-structured notification service
that follows the same architectural standards as the rest of the
distributed system.

Frontend/in-app notification integration belongs to a later phase after
the frontend exists.

Files reviewed:

-   `NotificationEventListener.java`
-   `NotificationService.java`
-   `KafkaConsumerConfig.java`
-   `KafkaProducerConfig.java`
-   `KafkaTopicsConfig.java`
-   `ObjectMapperConfig.java`
-   `application.yml`
-   `application-resilience.yml`
-   `pom.xml`
-   current Notification Service repository structure

------------------------------------------------------------------------

## 2. Current Architecture

Current flow:

``` text
Order / Payment / Inventory topics
              |
              v
    NotificationEventListener
              |
              v
      NotificationService
              |
              v
       Console output
```

Current responsibilities:

-   consumes domain events from Kafka;
-   maps `EventType` values to user-facing text;
-   writes notification messages to the console;
-   uses MDC fields for event/correlation logging;
-   contains Resilience4j annotations around listener processing;
-   manually publishes failed events to a notification DLQ from a
    fallback method;
-   registers with Eureka;
-   exposes Actuator/Prometheus metrics;
-   includes tracing/Zipkin configuration.

This is acceptable as an early proof of concept, but it is not yet a
durable notification subsystem.

------------------------------------------------------------------------

## 3. What Is Already Good

### 3.1 Event-driven boundary

The service consumes events asynchronously rather than requiring Order,
Payment, or Inventory to call it synchronously.

That is a good architectural direction because notification delivery is
naturally suited to asynchronous processing.

### 3.2 Shared event contracts

The service consumes the project's `DomainEvent` abstraction from
`shared-events`, giving it access to:

-   event metadata;
-   event type;
-   aggregate ID;
-   correlation ID;
-   event ID.

This keeps it aligned with the event model used by the other services.

### 3.3 Observability foundations

The listener already adds useful information to MDC:

-   `eventId`
-   `correlationId`
-   `aggregateId`
-   `eventType`

The service also contains Actuator, Prometheus and tracing
configuration.

These are useful foundations to retain while configuration is cleaned
up.

### 3.4 Service discovery and container compatibility

Eureka and Docker-oriented Kafka configuration already exist, so the
service participates in the broader local distributed-system
environment.

------------------------------------------------------------------------

# 4. Findings

## Finding N-01 --- Kafka topic names are stale and inconsistent

### Current state

`NotificationEventListener` consumes:

``` text
order.events
payment.events
inventory.events
```

The rest of the project has moved toward versioned topic names:

``` text
order.events.v1
payment.events.v1
inventory.events.v1
notification.events.v1
```

### Problem

Notification is currently out of alignment with the shared topic
contract.

Depending on which topics actually exist in the local Kafka environment,
this can cause Notification to consume from the wrong topics or receive
nothing at all.

### Phase 1 action

Replace hard-coded legacy names with the project's standardized topic
configuration/constants.

Topic ownership should be standardized across all services rather than
solved differently inside Notification.

**Priority:** Critical

------------------------------------------------------------------------

## Finding N-02 --- `KafkaTopicsConfig` duplicates topic ownership

### Current state

Notification contains:

``` java
public static final String NOTIFICATION_DLQ = "notification.events.dlq";
```

inside its own `KafkaTopicsConfig`.

The project already has shared topic definitions and is moving toward
consistent property-based topic configuration.

### Problem

Topic names are currently spread across:

-   shared constants;
-   service-local constants;
-   YAML;
-   listener annotations;
-   environment variables in parts of the wider project.

This creates multiple sources of truth.

### Phase 1 action

Remove/refactor `KafkaTopicsConfig` as part of the **cross-service
configuration-standardization task**.

Choose one consistent model for topic names and DLT names across Order,
Payment, Inventory, and Notification.

**Priority:** High

------------------------------------------------------------------------

## Finding N-03 --- Notification uses `DomainEvent` too generically

### Current state

A single listener receives:

``` java
public void onEvent(DomainEvent event)
```

and `NotificationService` switches on `event.getEventType()`.

### Problem

This is convenient for a proof of concept, but a real notification often
needs event-specific data.

For example:

``` text
PaymentFailedEvent
    -> reason

InventoryReservationFailedEvent
    -> reason

OrderCreatedEvent
    -> order/user/product information
```

The generic `DomainEvent` API exposes metadata and aggregate identity
but not every event-specific payload.

The service will become harder to evolve once templates need actual
event data.

### Phase 1 action

Define explicit notification handling boundaries.

Possible direction:

``` text
Kafka event
   |
   v
event-specific handler / mapper
   |
   v
NotificationRequest
   |
   v
NotificationService
```

Do not prematurely create a large handler framework; introduce only
enough structure to avoid one growing `switch`.

**Priority:** Medium/High

------------------------------------------------------------------------

## Finding N-04 --- Notification has no persistent notification model

### Current state

Notifications exist only as console output.

There is no reviewed:

-   `Notification` entity;
-   notification repository;
-   notification status;
-   delivery timestamp;
-   recipient;
-   channel;
-   failure information.

### Problem

Once the console line disappears, the system has no durable record of
what notification was created or delivered.

This also prevents later frontend integration from querying notification
history.

### Phase 1 action

Introduce a minimal persistent model.

A reasonable first version could conceptually contain:

``` text
Notification
- id
- eventId
- correlationId
- aggregateId/orderId
- recipient/userId (where available)
- type
- message
- channel
- status
- createdAt
- sentAt
```

Exact fields should be decided during implementation based on the
available event contracts.

**Priority:** High

------------------------------------------------------------------------

## Finding N-05 --- No notification lifecycle/status model

The service currently has only:

``` text
event received
    ->
console message
```

A basic durable notification service should distinguish lifecycle states
such as:

``` text
PENDING
SENT
FAILED
```

Potential future states can be introduced only if genuinely required.

### Phase 1 action

Add a small `NotificationStatus` model and make status transitions
explicit.

Avoid building a complex delivery state machine before there is a real
need.

**Priority:** Medium

------------------------------------------------------------------------

## Finding N-06 --- No idempotency protection

Order, Payment, and Inventory already contain forms of processed-event
handling.

Notification currently has no equivalent.

Kafka is an at-least-once processing environment, so the same event can
be delivered more than once.

Without idempotency:

``` text
PaymentCompletedEvent
        |
        +--> notification sent
        |
        +--> redelivery
                 |
                 +--> notification sent again
```

For console output this is harmless. For email, SMS, push, or frontend
notifications it becomes a user-visible duplicate.

### Phase 1 action

Add durable event idempotency.

However, do **not** blindly copy the older `existsById()` followed by
`save()` implementation from another service.

The audit has already identified concurrency/transactional weaknesses in
that pattern.

The Phase 1 design should use a database-enforced uniqueness strategy
and ensure that marking an event as processed is coordinated correctly
with notification persistence.

**Priority:** Critical before real delivery channels

------------------------------------------------------------------------

## Finding N-07 --- Resilience4j retry overlaps with Kafka recovery

### Current state

The listener uses:

``` text
@Retry
@CircuitBreaker
@Bulkhead
@KafkaListener
```

and the fallback manually publishes to a DLQ.

### Problem

This repeats the concern found in the other audited services.

Kafka consumer failures should primarily be handled through Spring
Kafka's consumer error-handling model rather than stacking independent
retry systems around the listener.

Otherwise one logical failure can pass through multiple retry mechanisms
and produce difficult-to-reason-about behaviour.

### Phase 1 action

Standardize listener recovery around:

``` text
Kafka listener
      |
      v
processing
      |
      +-- success --> commit/progress
      |
      +-- failure
             |
             v
      DefaultErrorHandler
             |
             v
          BackOff
             |
             v
             DLT
```

Review whether circuit breaker or bulkhead behaviour has a legitimate
responsibility after this change rather than keeping annotations merely
because Resilience4j is installed.

**Priority:** High

------------------------------------------------------------------------

## Finding N-08 --- Manual DLQ publication should be replaced by standardized Kafka DLT handling

### Current state

The listener fallback performs:

``` java
kafkaTemplate.send(
    KafkaTopicsConfig.NOTIFICATION_DLQ,
    event.getAggregateId().toString(),
    event
);
```

### Problem

The other services already contain `DefaultErrorHandler` +
`DeadLetterPublishingRecoverer` patterns.

Notification currently implements a different failure model.

This means recovery semantics differ from service to service.

### Phase 1 action

Move Notification to the same Spring Kafka error-handler/DLT model
selected for the other services.

The listener should focus on business processing; infrastructure
recovery should live in Kafka configuration.

**Priority:** High

------------------------------------------------------------------------

## Finding N-09 --- Kafka consumer configuration is behind the other services

Notification's consumer config currently lacks some of the mechanisms
already present elsewhere, including the standardized
`DefaultErrorHandler` path.

It also uses a simpler deserializer configuration and concurrency of
`1`.

Concurrency `1` is not automatically wrong. It may actually be useful
where ordering matters. However, concurrency should be chosen based on:

-   topic partition count;
-   ordering requirements;
-   expected throughput;
-   delivery-provider limits.

### Phase 1 action

Bring the consumer configuration into the shared Kafka configuration
standard while making concurrency an intentional choice rather than
copying `3` from other services.

**Priority:** Medium/High

------------------------------------------------------------------------

## Finding N-10 --- Producer configuration is hard-coded

### Current state

The producer contains:

``` java
BOOTSTRAP_SERVERS_CONFIG = "kafka:9092"
```

while `application.yml` also contains:

``` yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
```

### Problem

Java configuration and YAML both own the same setting.

This reduces portability between:

``` text
local
docker
test
future production
```

### Phase 1 action

Use Spring's externalized configuration consistently.

Remove environment-specific Kafka addresses from Java classes.

**Priority:** High

------------------------------------------------------------------------

## Finding N-11 --- Configuration is duplicated between Java and YAML

Kafka producer/consumer settings are declared partly in Java and partly
in YAML.

Examples include:

-   bootstrap servers;
-   retries;
-   serializers/deserializers;
-   consumer settings.

### Problem

It becomes unclear which configuration is authoritative.

This issue is not unique to Notification and should not be fixed locally
in isolation.

### Phase 1 action --- Cross-service configuration standardization

Apply one consistent configuration strategy across:

-   Order Service;
-   Payment Service;
-   Inventory Service;
-   Notification Service.

Target mental model:

``` text
application.yml
    -> common service configuration

application-local.yml
    -> local overrides

application-docker.yml
    -> Docker overrides

application-test.yml
    -> test overrides

config/resilience.yml
    -> explicitly imported concern-specific configuration

future application-prod.yml
    -> production-specific non-secret configuration
```

Environment-specific values should flow through Spring configuration
rather than direct `System.getenv()` calls scattered through Java
configuration.

**Priority:** High / Cross-service

------------------------------------------------------------------------

## Finding N-12 --- `application-resilience.yml` loading must be verified

### Current state

The project contains:

``` text
application-resilience.yml
```

### Important clarification

Spring interprets:

``` text
application-{profile}.yml
```

as profile-specific configuration.

Therefore `application-resilience.yml` naturally represents a profile
named `resilience`.

The presence of:

``` xml
resilience4j-spring-boot3
```

does **not** automatically cause that file to load.

### Phase 1 action

This applies to all audited services using the same structure.

Decide whether resilience is:

1.  genuinely an optional Spring profile; or
2.  configuration that should always be loaded.

For this project, the likely cleaner direction is concern-based explicit
import, for example conceptually:

``` text
application.yml
      |
      +--> config/resilience.yml
```

while reserving profiles for environments such as:

``` text
local
docker
test
prod
```

This must be implemented consistently across services.

**Priority:** High / Cross-service

------------------------------------------------------------------------

## Finding N-13 --- Resilience configuration itself needs reassessment

Current `application-resilience.yml` contains retry and circuit-breaker
configuration for `notification-kafka`.

If listener-level `@Retry` and potentially other annotations are removed
during Kafka recovery standardization, corresponding unused
configuration should also be removed.

### Phase 1 action

Do not merely make the file load correctly.

First determine which resilience mechanisms still have a valid
responsibility.

Then keep only configuration that maps to real runtime behaviour.

**Priority:** Medium/High

------------------------------------------------------------------------

## Finding N-14 --- ObjectMapper configuration is duplicated across services

Notification contains:

``` java
@Bean
public ObjectMapper objectMapper() {
    return EventObjectMapperFactory.get();
}
```

The same pattern exists in the other audited services.

### Assessment

The shared event module currently owns `EventObjectMapperFactory`, but
each service still needs to expose/configure the mapper within its
Spring context.

The duplication itself is small, but it belongs to the broader question
of how much infrastructure configuration should be shared.

### Phase 1 action

Review this during cross-service configuration standardization.

Avoid turning `shared-events` into a dumping ground for arbitrary Spring
infrastructure. Its primary responsibility should remain shared event
contracts.

If common Spring Kafka/Jackson auto-configuration is eventually
extracted, it should have an intentional module/design rather than
accumulating unrelated configuration in `shared-events`.

**Priority:** Low/Medium

------------------------------------------------------------------------

## Finding N-15 --- Trusted packages wildcard is too broad

Current configuration uses:

``` text
spring.json.trusted.packages: "*"
```

and/or:

``` java
deserializer.addTrustedPackages("*");
```

### Problem

Wildcard trust is convenient during development but unnecessarily broad.

### Phase 1 action

Restrict deserialization to the project's event packages where
practical, for example the `shared_events` namespace.

Test polymorphic/event deserialization carefully when doing this.

**Priority:** Medium

------------------------------------------------------------------------

## Finding N-16 --- Notification currently has no database dependency

The reviewed `pom.xml` contains no JPA/MySQL dependency for durable
notification storage.

That matches the current console-only implementation.

### Phase 1 action

If persistent notifications are introduced, add the appropriate
persistence dependencies and give Notification its own database/schema
in accordance with the project's database-per-service approach.

Do not let Notification directly read Order, Payment, or Inventory
databases.

**Priority:** High when persistence is implemented

------------------------------------------------------------------------

## Finding N-17 --- Notification needs a delivery abstraction before real channels are added

Current implementation:

``` java
private void send(String message) {
    System.out.println(...);
}
```

### Phase 1 action

Keep console delivery initially, but hide it behind a small delivery
boundary.

Conceptually:

``` text
NotificationService
        |
        v
NotificationSender / DeliveryChannel
        |
        +--> ConsoleNotificationSender   [Phase 1]
        |
        +--> EmailNotificationSender     [later]
        |
        +--> InAppNotificationSender     [frontend phase]
```

This lets Phase 1 remain simple while avoiding a rewrite when a frontend
or external provider is introduced.

Do not add email/SMS integrations merely to make the project appear more
enterprise-like.

**Priority:** Medium

------------------------------------------------------------------------

## Finding N-18 --- Message construction should evolve toward templates/mappers

Current notification text is embedded directly in a switch statement.

Example:

``` text
PAYMENT_COMPLETED -> "Payment successful!"
```

This is acceptable today.

As notification types grow, however, business event interpretation and
message rendering should not remain inside one large switch.

### Phase 1 action

Introduce a modest mapping/template layer when persistence is
implemented.

Do not introduce a full template engine unless there is a real
requirement.

**Priority:** Medium

------------------------------------------------------------------------

## Finding N-19 --- Recipient identity is not consistently available

A notification system ultimately needs to know **who** receives a
notification.

The generic `DomainEvent` contract does not guarantee a `userId`, and
several downstream events may not carry one.

### Phase 1 investigation

Decide how Notification obtains recipient identity without coupling
itself to another service's database.

Possible approaches should be evaluated against the event flow during
Phase 1.

Do not silently invent recipient data.

**Priority:** High before user-facing delivery

------------------------------------------------------------------------

## Finding N-20 --- Notification producer may become unnecessary

The current Kafka producer primarily exists because the listener
fallback manually sends failed events to a notification DLQ.

If DLT publication moves to `DeadLetterPublishingRecoverer`, reassess
whether Notification requires a general-purpose producer at all.

A service should not retain infrastructure simply for symmetry with
other services.

**Priority:** Medium

------------------------------------------------------------------------

## Finding N-21 --- Observability dependencies need consolidation

The POM currently includes both Brave-oriented and
OpenTelemetry-oriented tracing dependencies:

-   `micrometer-tracing-bridge-brave`
-   `zipkin-reporter-brave`
-   `opentelemetry-exporter-zipkin`
-   `micrometer-tracing`
-   `micrometer-tracing-bridge-otel`

### Problem

This suggests potentially overlapping tracing stacks.

### Phase 1 action

Audit the tracing strategy across the entire project and choose one
coherent Micrometer tracing bridge/export path.

Do this as a cross-service observability task rather than fixing
Notification independently.

**Priority:** Medium/High / Cross-service

------------------------------------------------------------------------

## Finding N-22 --- `spring-boot-starter-web` should be justified

Notification currently depends on the web starter although the reviewed
service has no controller.

Actuator endpoints may still require an HTTP server depending on the
chosen deployment/management model, so this should not be removed
blindly.

### Phase 1 action

Determine whether Notification needs a normal servlet web application or
only management endpoints/event processing.

Keep/remove the dependency based on an explicit runtime requirement.

**Priority:** Low

------------------------------------------------------------------------

# 5. Recommended Phase 1 Target Architecture

The Phase 1 target should remain intentionally modest:

``` text
Order / Payment / Inventory events
               |
               v
       Kafka Listener(s)
               |
               v
      Idempotency boundary
               |
               v
       Event -> Notification
            mapping
               |
               v
       NotificationService
          /          \
         v            v
   Persistence    Delivery abstraction
                      |
                      v
             Console delivery initially

Failure
   |
   v
DefaultErrorHandler
   |
   v
BackOff / retries
   |
   v
DLT
```

This is enough to make Notification architecturally credible without
prematurely introducing external providers.

------------------------------------------------------------------------

# 6. Suggested Phase 1 Domain Structure

A possible structure to work toward:

``` text
notification-service/
└── src/main/java/com/kafka_implementation/notification_service/
    ├── config/
    │   └── Kafka configuration
    │
    ├── consumer/
    │   └── NotificationEventListener
    │
    ├── domain/
    │   ├── Notification
    │   ├── NotificationStatus
    │   ├── NotificationType
    │   └── ProcessedEvent
    │
    ├── repository/
    │   ├── NotificationRepository
    │   └── ProcessedEventRepository
    │
    ├── service/
    │   └── NotificationService
    │
    ├── delivery/
    │   ├── NotificationSender
    │   └── ConsoleNotificationSender
    │
    └── mapping/
        └── event-to-notification mapping
```

This is a direction, not a requirement to create every class
immediately.

We should introduce structure only when its responsibility becomes real.

------------------------------------------------------------------------

# 7. Frontend Evolution --- Later Phase

The frontend should **not** be part of the initial Notification Phase 1
refactor.

After persistence and a frontend exist, Notification can evolve toward:

``` text
Kafka events
    |
    v
Notification Service
    |
    +--> notification database
    |
    +--> frontend API
    |
    +--> optional real-time delivery
             |
             +--> SSE
             or
             +--> WebSocket
```

Possible UI functionality later:

-   notification bell;
-   unread count;
-   notification history;
-   read/unread state;
-   order/payment/inventory status messages.

Real-time delivery should be added only if it improves the project and
learning goals.

------------------------------------------------------------------------

# 8. Phase 1 Priority Order

Recommended implementation order:

1.  **Standardize topic names** and remove legacy `order.events`,
    `payment.events`, and `inventory.events`.
2.  **Standardize cross-service configuration ownership**, including
    environment profiles versus imported concern configuration.
3.  **Replace listener Resilience4j retry/manual DLQ behaviour** with
    the chosen Spring Kafka `DefaultErrorHandler` + DLT policy.
4.  **Add durable idempotency** suitable for concurrent/redelivered
    Kafka events.
5.  **Introduce Notification persistence** and a minimal status model.
6.  **Introduce event-to-notification mapping** rather than allowing the
    current switch to grow indefinitely.
7.  **Introduce a delivery abstraction**, retaining console output as
    the first implementation.
8.  **Resolve recipient identity** for events that do not currently
    expose enough information.
9.  **Consolidate tracing/observability dependencies** across services.
10. Add frontend/in-app delivery in a later phase.

------------------------------------------------------------------------

# 9. Cross-Service Findings Added From This Audit

The following should be tracked at system level rather than only inside
Notification.

### CS-01 --- Configuration standardization

Standardize:

``` text
application.yml
application-local.yml
application-docker.yml
application-test.yml
future production configuration
explicitly imported concern configuration
```

Reserve profiles primarily for environment/runtime differences.

### CS-02 --- Remove competing configuration ownership

Avoid defining the same Kafka endpoint/settings simultaneously in:

``` text
Java constants
System.getenv()
application.yml
Docker environment variables
service-local topic constants
shared topic constants
```

Use Spring externalized configuration as the central mechanism.

### CS-03 --- Kafka recovery standardization

Across Order, Payment, Inventory, and Notification:

``` text
listener exception
    ->
DefaultErrorHandler
    ->
controlled backoff/retry
    ->
DLT
```

Avoid overlapping listener-level Resilience4j retries unless a distinct
responsibility is demonstrated.

### CS-04 --- Resilience configuration loading

Verify every existing `application-resilience.yml`.

A Resilience4j dependency does not automatically load that file.

Decide whether resilience configuration is a profile or an explicitly
imported concern.

### CS-05 --- Observability dependency alignment

Review Brave/OpenTelemetry/Micrometer tracing dependencies and establish
one project-wide strategy.

### CS-06 --- Shared infrastructure boundaries

Do not solve duplication by gradually moving arbitrary Spring
configuration into `shared-events`.

`shared-events` should remain primarily an event-contract module unless
a deliberate shared-infrastructure module is designed later.

------------------------------------------------------------------------

# 10. What We Should NOT Add Yet

To keep Phase 1 focused, Notification does not currently need:

-   SMS integration;
-   production email provider;
-   push notification provider;
-   WebSockets immediately;
-   SSE immediately;
-   sophisticated template engines;
-   notification preference center;
-   multi-channel routing engine;
-   complex scheduling;
-   large provider abstraction frameworks.

These can become later improvements if they add genuine architectural or
learning value.

------------------------------------------------------------------------

# 11. Phase 0 Conclusion

Notification Service is currently a **valid proof of concept**, but it
is significantly behind Order and Inventory in persistence and Kafka
failure-handling maturity.

Its strongest existing architectural choice is that notifications are
already event-driven.

The main Phase 1 objective is therefore not to replace the service, but
to evolve it:

``` text
console event consumer
        |
        v
durable + idempotent notification service
        |
        v
clean Kafka recovery
        |
        v
delivery abstraction
        |
        v
console implementation
        |
        v
future frontend / in-app delivery
```

The most important immediate issues are:

-   stale Kafka topic names;
-   no idempotency;
-   no notification persistence;
-   manual DLQ handling;
-   overlapping Kafka/Resilience4j recovery;
-   duplicated/hard-coded configuration;
-   unclear loading of `application-resilience.yml`;
-   no durable recipient/delivery model.

The service does **not** need to become a large enterprise notification
platform during Phase 1.

The correct Phase 1 outcome is a **small, coherent, durable, observable
and consistently configured service** that fits the architecture of the
rest of the project and leaves clean extension points for the future
frontend.

------------------------------------------------------------------------

## Phase 0 Status

**Notification Service audit: COMPLETE**

Next implementation stage:

``` text
Phase 1
  |
  +--> cross-service configuration standardization
  +--> Kafka reliability/recovery standardization
  +--> service-specific refactoring
  +--> Notification Service minimum credible implementation
```
