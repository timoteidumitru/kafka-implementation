# Order Service — Architecture Audit & Implementation Ledger

**Repository:** `kafka-microservices` · **Module:** `order-service`  
**Origin:** Phase 0 architecture audit (13 October 2026)  
**Reconciled through:** Phase 1, CSA-04A.9 technical recovery completion (31 October 2026)  
**Status:** Phase 0 audit complete; Phase 1 **in progress**; last full Order Service regression **GREEN: 127/127**  
**Document purpose:** The single entry point for what was found, what was implemented, what was verified, and what must be done next. Do not treat old Phase 0 observations as descriptions of the current code without consulting the current-status column.

> **Evidence convention:** `VERIFIED` means confirmed by code shared in the review and/or a named successful test. `IMPLEMENTED / REVIEW` means an implementation is discussed or visible but its precise current production configuration was not re-audited in this reconciliation. `OPEN` means not shown resolved. `PARTIAL` means some aspects are verified but others remain. `BASELINE` refers to the October 13 Phase 0 snapshot, not necessarily the current source tree. Never turn a proposed design or passing test into a broader untested guarantee.

---

## 0. Restart here — current status in one minute

- **Completed milestones:** `CSA-04A.8` — business-effect idempotency and processor-level concurrent transaction atomicity (30 Oct); `CSA-04A.9` — domain conflict exception, Kafka non-retryable classification, and real-broker listener-to-DLT recovery **VERIFIED GREEN** (31 Oct).
- **Next active item:** `CSA-04A.10` — DLT reconciliation and safe replay: investigation, alerting, replay/compensation criteria and operator runbook. **OPEN**.
- **Last full verification (31 Oct 2026):** Windows CMD `.\mvnw.cmd -pl order-service -am clean verify` — **59 unit tests + 68 integration tests = 127 passed**, zero failures, errors or skipped; reactor `BUILD SUCCESS`. This is the confirmed suite at this checkpoint, not a guarantee about later edits.
- **Current verified improvements:** transactional event claim, business-effect idempotency, domain state machine, optimistic version checking, transactional outbox, outbox recovery/fencing, DLT/offset safety, **dedicated incompatible-transition exception**, non-retryable Kafka conflict classification, and real-broker payment DLT routing for a simulated processor conflict.
- **Not established:** end-to-end exactly-once Kafka delivery; a single integration test combining *real database processor* with *real Kafka broker* for a conflict (these layers were tested separately); operator reconciliation/replay safety; complete production profile configuration audit; authoritative price validation; resolution of all Phase 0 issues.
- **Audit hygiene:** retain historical findings with their original IDs, but work only from the **OPEN/PARTIAL** actions below. When closing an item, add code references, test names, regression command, date and suggested commit.

### Current architecture (Phase 1 understanding)

```text
POST /api/orders via API Gateway
    -> OrderController / transactional Order creation
    -> orders + outbox_event in same MySQL transaction
    -> OutboxRelay / Kafka publisher -> order.events.v1

payment.events.v1 / inventory.events.v1
    -> OrderEventListener (Kafka adapter)
    -> OrderEventProcessor (@Transactional)
         -> IdempotencyGuard.tryClaim(eventId)  [processed_event]
         -> OrderService.complete/cancel(orderId)
         -> domain transition indicates changed / already terminal / invalid
         -> OutboxWriter.store(lifecycleEvent) ONLY when changed
    -> transaction commits claim + Order update + outgoing outbox together
    -> relay publishes pending outbox records (at-least-once publication possible)
```

**Important:** the above reflects reviewed application flow. Kafka topic processing order across different topics is not globally guaranteed. Incompatible committed terminal outcomes now throw `InvalidOrderStateTransitionException`; the listener propagates it and the Kafka handler treats it as non-retryable, publishing to the appropriate `.DLT` when recovery succeeds. **DLT delivery does not reconcile the contradictory business outcome.**

---

## 1. Phase 0 finding-to-Phase 1 status matrix

**Status meanings:** ✅ Verified resolved within stated scope · 🟡 Partial / verify remaining cases · ⬜ Open / not evidenced · 📌 Keep/monitor.

| Phase 0 ID | Finding (preserved) | Current status | Verified evidence / remaining action |
|---|---|---|---|
| 03 | Consumer idempotency transaction boundary | ✅ **VERIFIED** | `OrderEventProcessor` transactional methods; `IdempotencyGuard` has `MANDATORY` propagation. `OrderServiceIT` tests rollback and claim reuse. |
| 04 | Idempotency `exists`→`insert` race | ✅ **VERIFIED** | Atomic `INSERT IGNORE` to unique `processed_event.event_id`, strict warning classification; concurrent claim and cross-transaction tests; `IdempotencyGuardTest` warning tests. |
| 05 | Different events race for same Order | ✅ **VERIFIED** (DB atomicity) | `@Version` + domain transition rules; real MySQL entity race and real `OrderEventProcessor` race GREEN. Losing processed-event claim and terminal outbox write roll back. **Retry of contradictory event remains CSA-04A.9.** |
| 06 | Lifecycle DB/Kafka dual write | ✅ **VERIFIED** (DB/outbox atomicity) | Transactional outbox in Order Service, lifecycle-event rollback tests and outbox relay tests. **Kafka relay remains at-least-once.** |
| 07 | Initial Order creation dual write | ✅ **VERIFIED** | Creation persists Order + `OrderCreatedEvent` outbox atomically; rollback test when outbox writing fails. |
| 08 | Overlapping listener retry mechanisms | 🟡 **PARTIAL** | Reviewed `OrderEventListener` has no `@Retry`, `@CircuitBreaker`, `@Bulkhead` or fallback; `KafkaConsumerConfig` installs `DefaultErrorHandler`. Still verify other profile/config layers before claiming full-system closure. |
| 09 | Circuit-breaker fallback can swallow failures | 🟡 **PARTIAL** | Reviewed `OrderEventListener` propagates processor exceptions (MDC cleaned in `finally`); direct handler and real Kafka DLT tests GREEN. Profile-specific/wider configuration inspection still open. |
| 10 | Unrestricted Order state machine | ✅ **VERIFIED** | `CREATED -> COMPLETED/CANCELLED`; same-terminal no-op; opposite-terminal throws; `OrderTest`, integration tests. |
| 11 | JPA `@Data` / entity encapsulation | ⬜ **VERIFY SOURCE** | Original risk persists unless `Order.java` accessors were deliberately constrained; no closure evidence captured. |
| 12 | API request validation | ⬜ **OPEN / VERIFY** | Validate non-null IDs, positive quantity/price, `@Valid` and controller error contract; no verified closure. |
| 13 | Client controls payment price | ⬜ **OPEN — HIGH RISK** | Do not accept client-supplied price as authoritative charge amount; decide trusted pricing policy/owner. |
| 14 | Serializer / type-header mismatch | 🟡 **PARTIAL** | Inventory serialization and routing unit tests GREEN; full producer/consumer configuration consistency still requires audit. |
| 15 | Multiple listeners on shared inventory topic | 🟡 **PARTIAL** | `OrderEventRoutingTest`, `OrderKafkaListenerIT` GREEN; confirm routing/consumer-group assumptions under production config. |
| 16 | Failure classification | 🟡 **PARTIAL** | **CSA-04A.9 technical classification VERIFIED:** `InvalidOrderStateTransitionException` now non-retryable, alongside unsupported version/type; ordinary failures retain bounded retry in tested cases. Real Kafka test verifies immediate payment DLT delivery. Wider operational replay/reconciliation and profile review open. |
| 17 | Service outage vs consumer/DLT handling | 📌 **KEEP / MONITOR** | Kafka retains unconsumed records within retention; implement observable lag/backlog, DLT alarms and runbook. Not a code bug itself. |
| 18 | Multiple Kafka configuration sources | ⬜ **OPEN / VERIFY** | Review Java bean config against YAML profiles (`local`, `docker`, `test`, resilience) for precedence and drift. |
| 19 | Inconsistent Kafka bean names | ⬜ **OPEN / VERIFY** | Audit `KafkaProducerConfig`/`KafkaConsumerConfig`; rename leftover `inventory*` names only if still present. |
| 20 | Shared ObjectMapper strategy | ⬜ **OPEN / VERIFY** | Keep `shared-events` framework-light; compare mapper configuration and polymorphic event compatibility. |
| 21 | Scattered literal event versions | ⬜ **OPEN** | Prefer contract-owned version constants where useful; `next(metadata, "order-service", 1)` currently used. |
| 22 | Unused request event contracts | ⬜ **OPEN / VERIFY** | Repository-wide usage check of `PaymentRequestedEvent` / `InventoryReserveRequestedEvent` before removal. |
| 23 | DLT constants vs naming convention | ⬜ **OPEN / VERIFY** | Decide whether named constants or `.DLT` derivation is the source of truth. |
| 24 | Business/operational observability | 🟡 **PARTIAL** | Tracing/MDC, Prometheus and health foundation present; define counters/gauges for Order lifecycle, DLT, consumer lag and outbox. |
| 25 | Ordered Phase 1 implementation priorities | 📌 **TRACKING** | Replaced with active backlog and verification gates below; do not restart closed tasks. |
| 26 | Avoid unnecessary new technologies | 📌 **KEEP** | No new service, saga framework, pessimistic lock, or redundant retry layer without justified need. |

**Phase 0 section 02 (“KEEP — existing good decisions”)** remains architectural context rather than an issue: Order is public write entry point, database-per-service, Kafka event metadata/correlation, versioned topics, DLT foundation, observability. **Phase 0 sections 27–28** remain conceptual lessons/conclusion and are updated in §§7–8 below.

---

## 2. Phase 1 implementation ledger — completed and evidence

### CSA-04A — Order correctness and delivery reliability

| Work item | Implementation / decision | Verification | Status |
|---|---|---|---|
| Consumer atomicity | `OrderEventProcessor` is the transaction boundary; `IdempotencyGuard.tryClaim` joins existing transaction (`MANDATORY`). Claim, Order mutation, and outbox write commit/rollback together. | `OrderServiceIT.shouldAllowEventToBeClaimedAgainAfterRollback`, duplicate/redelivery tests, outbox-failure rollback tests. | ✅ Verified for tested paths |
| Atomic event-ID dedup | MySQL uniqueness with `INSERT IGNORE`; distinguishes duplicate-key warning `1062` from unexpected SQL warnings; does **not** use unsafe `existsById` before insert. | `IdempotencyGuardTest` four warning-classification tests; `OrderServiceIT.shouldAllowOnlyOneConcurrentAtomicClaim`, `shouldAtomicallyClaimIncomingEventOnlyOnce`, `shouldEnforceUniqueProcessedEventIdAcrossTransactions`. | ✅ Verified |
| Transactional outbox for creation | Order + outgoing created event saved in one local DB transaction; relay publishes independently. | `shouldRollbackOrderCreationWhenOutboxWritingFails` and existing outbox tests. | ✅ Verified for DB atomicity |
| Transactional outbox for terminal outcomes | Order status mutation + `OrderCompletedEvent` / `OrderFailedEvent` outbox storage in one local transaction. | `shouldRollbackOrderAndOutboxWhenOutboxWritingFails`; relevant duplicate and relay tests. | ✅ Verified for tested paths |
| Outbox relay/recovery/fencing | Dedicated `OutboxWriter`, `OutboxClaimService`, `OutboxKafkaPublisher`, `OutboxPublicationStateService`, `OutboxRecoveryService`, `OutboxRecoveryScheduler`, `OutboxRelay`; tests for claim/lease/recovery behavior. | `OutboxRelayTest`, `OutboxRecoveryScheduler*Test`, `OutboxKafkaPublisherTest`, service IT outbox lease/fencing cases. | ✅ Existing tested capability; operational review remains |
| Domain invariants | `markCompleted()` / `markCancelled()` allow only transition from `CREATED`; same-target terminal returns no change; incompatible terminal state throws. | `OrderTest`, `OrderServiceIT` domain tests. | ✅ Verified |
| Optimistic locking | `Order` has `@Version Long version`, detecting stale concurrent writes. | `shouldAllowOnlyOneConcurrentTerminalTransitionToCommit` (2 threads, distinct transactions, latch-coordinated reads/flush; MySQL GREEN). | ✅ Verified at entity/JPA layer |
| **CSA-04A.8 — processor-level concurrent rollback** | Two distinct incoming event IDs race (`InventoryReservedEvent` vs `PaymentFailedEvent`) against one `CREATED` Order; `@SpyBean OutboxWriter` + two latches align the competing transactions at terminal outbox write without adding production hooks. Exactly one transaction commits; loser rolls back claim, Order mutation and outbox insert. | `OrderServiceIT.shouldRollbackLosingClaimAndOutboxOnConcurrentTerminalTransitions` — focused MySQL Testcontainers integration test GREEN (30 Oct 2026), followed by full suite GREEN. | ✅ **VERIFIED** for tested concurrent workflow |
| **Business-effect idempotency** | Domain transition returns `boolean transitioned`; `OrderService.complete/cancel` propagate it; `OrderEventProcessor` stores lifecycle outbox event only if `transitioned=true`; incoming distinct event ID still claimed when same outcome already committed. | Characterization GREEN (two completion events), desired RED (expected one, got two), fixed GREEN `shouldCreateOnlyOneCompletionEventForDistinctIncomingEventIds`; `shouldCreateOnlyOneFailureEventForDistinctPaymentFailureEventIds`; `shouldNotCreateAnotherFailureEventWhenPaymentFailsAfterInventoryFailure`. | ✅ Verified sequential cases (29 Oct) |
| Kafka DLT/offset resilience | Spring Kafka `DefaultErrorHandler`/recoverer tested for failure and retry behavior, including DLT publication failure and offset safety. | `KafkaDltRecoveryTest`, `OrderKafkaDltIT`, `OrderKafkaDltOffsetSafetyIT`, `OrderKafkaListenerIT`. | ✅ Test evidence; review retry-annotation policy |
| **CSA-04A.9 — permanent domain conflicts** | `Order.markCompleted/markCancelled` throw `InvalidOrderStateTransitionException extends IllegalStateException` for incompatible terminal transitions. `KafkaConsumerConfig` classifies it non-retryable while retaining `FixedBackOff(1000L, 3L)` for ordinary retryable exceptions. Real listener routes simulated `PaymentFailedEvent` conflict to `payment.events.v1.DLT` on first processing attempt. | `OrderTest.shouldThrowSpecificExceptionWhenCancellingCompletedOrder`; `OrderServiceIT.shouldRejectPaymentFailureAfterOrderCompletedWithoutSideEffects`; `KafkaDltRecoveryTest.shouldSendInvalidOrderStateTransitionToDltWithoutRetries`; `OrderKafkaDltIT.shouldSendTerminalStateConflictToPaymentDltWithoutRetries`; complete 127-test regression (31 Oct). | ✅ **VERIFIED** for tested technical recovery paths; operational reconciliation OPEN |
| Schema migrations | Flyway migration scripts `V1__create_order_service_schema.sql` and `V2__processed_event_timestamp_not_null.sql`; MySQL Testcontainers migration tests. | `FlywayChecksumIT`, `FlywayPartialMigrationIT`, full suite. | ✅ Verified migration test coverage |

### Business-effect idempotency: exact tested contract

```text
1) Incoming eventId already claimed       -> processor returns false; no side effect.
2) New eventId, Order CREATED             -> claim; transition; one lifecycle outbox event; return true.
3) New eventId, same already terminal state -> claim; transition returns false; no new lifecycle outbox; return true.
4) New eventId, incompatible terminal state -> exception; transaction rolls back claim and side effects.
```

**Contract note:** `OrderEventProcessor`'s boolean reports that the incoming event was newly accepted/handled (`true`) versus duplicate event ID (`false`); it does **not** mean a state transition occurred. The internal `transitioned` boolean carries that distinction. Preserve this when refactoring listeners or tests.

**Consequence:** A distinct late failure event after a completed order, or late success after a cancelled order, is not a harmless same-state repeat. The domain rejects it with `InvalidOrderStateTransitionException`; the Kafka handler classifies that exception as non-retryable and routes to the source topic’s `.DLT` on successful recovery. **The operational decision to reconcile, compensate, or replay such events remains OPEN (`CSA-04A.10`).**

### Test outcomes — latest verified full regression (CSA-04A.9 technical work closed)

Executed from repository root in **Windows Command Prompt (CMD)** on **31 October 2026**:

```bat
.\mvnw.cmd -pl order-service -am clean verify > "%TEMP%\order-csa-04a9-final.log" 2>&1
findstr /C:"Tests run:" /C:"BUILD SUCCESS" /C:"BUILD FAILURE" "%TEMP%\order-csa-04a9-final.log"
```

| Suite | Count | Outcome |
|---|---:|---|
| Surefire unit tests | 59 | All PASS |
| Failsafe integration tests | 68 | All PASS |
| **Combined** | **127** | **0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |

`OrderServiceIT`: **53** passing integration tests (including the new late-conflict processor test). `OrderKafkaDltIT`: **4** integration tests (including simulated conflict routed through the real Kafka listener and broker). Other integration tests: DLT offset safety IT 1, Kafka listener IT 4, Flyway checksum IT 1, Flyway partial migration IT 1, Order repository IT 4 (**15** outside `OrderServiceIT`; **68** total). `KafkaDltRecoveryTest`: **6** passing unit tests; `OrderTest`: **8** passing unit tests.

The `ERROR` stack traces in `KafkaDltRecoveryTest` for `Simulated DLT broker failure` and `DLT broker temporarily unavailable` are intentional test-injected failures. Both recovery-failure tests passed; judge the outcome by the Maven summary.

**Scope of verified guarantee:** `OrderServiceIT` proves DB state/claim/outbox rollback for a late conflict; `KafkaDltRecoveryTest` proves immediate handler recovery for the dedicated exception; `OrderKafkaDltIT` proves the real listener/consumer/broker publishes a *mock-processor-generated* conflict to `payment.events.v1.DLT` after one invocation. This is **not** one combined end-to-end real-database-and-real-Kafka test, proof of committed offsets for this exact new case, exactly-once Kafka delivery, or a reconciliation workflow.

---

## 3. Active engineering backlog — ranked, not already completed

### Closed — CSA-04A.8: business lifecycle idempotency and processor-level concurrency (30 Oct 2026)

**Status:** ✅ COMPLETE for the tested database correctness and rollback invariants. Preserve these tests; do not re-open this item merely because recovery policy remains outstanding.

**Focused integration test:** `OrderServiceIT.shouldRollbackLosingClaimAndOutboxOnConcurrentTerminalTransitions` uses real `OrderEventProcessor` calls for a distinct inventory-success and payment-failure event on the same Order. Two independent threads reach a Mockito spy barrier at terminal outbox writes, then race. Assertions establish:

- Both processors reached the barrier after proposing conflicting transitions.
- Exactly one processor transaction committed; the other failed with an optimistic-lock conflict.
- Exactly one terminal Order state and matching terminal outbox event committed (plus the original `ORDER_CREATED` outbox event).
- Winning event ID remained in `processed_event`; the losing claim did not survive rollback.

**Verification:** focused GREEN on 30 Oct 2026, then full clean verify GREEN (**57 Surefire + 66 Failsafe = 123 passing**).

**Boundary:** A later retry of the losing contradictory event is a separate business/recovery concern, tracked as `CSA-04A.9`. This test did not exercise a Kafka broker or a retried conflicting event.

### Closed — CSA-04A.9: terminal-state conflicts and Kafka recovery classification (31 Oct 2026)

**Status:** ✅ COMPLETE **for tested technical exception classification and listener-to-DLT behaviour**. Do not conflate this with operational reconciliation.

- Domain: introduced `domain/InvalidOrderStateTransitionException.java` extending `IllegalStateException`; `Order.markCompleted()` and `markCancelled()` retain allowed transitions and idempotent same-state no-ops, while contradictory transitions throw the dedicated exception.
- Processor characterization: `OrderServiceIT.shouldRejectPaymentFailureAfterOrderCompletedWithoutSideEffects` demonstrates a late `PaymentFailedEvent` after successful inventory completion: Order stays `COMPLETED`, conflicting processed-event claim is rolled back, no `ORDER_FAILED` outbox event is persisted.
- Kafka: reviewed `OrderEventListener` contains no Resilience4j annotations/fallback; it delegates and propagates exceptions. `KafkaConsumerConfig` installs a `DefaultErrorHandler` with `FixedBackOff(1000L, 3L)` and marks incompatible terminal transitions plus unsupported event versions/types non-retryable.
- Unit handler: `KafkaDltRecoveryTest.shouldSendInvalidOrderStateTransitionToDltWithoutRetries` verifies DLT recovery on first handler call, and existing retryable and DLT-send-failure tests still pass.
- Real broker: `OrderKafkaDltIT.shouldSendTerminalStateConflictToPaymentDltWithoutRetries` publishes `PaymentFailedEvent`, mocks the processor to throw the domain exception, verifies one invocation, source metadata, and delivery to `payment.events.v1.DLT` using Testcontainers Kafka.
- **Verification:** full `clean verify` on 31 Oct 2026, **59 unit + 68 integration = 127 passed**, `BUILD SUCCESS`.

**Boundary:** `OrderKafkaDltIT` uses `@MockBean OrderEventProcessor`; it does not independently verify real DB rollback. The DB-backed test verifies that separately. Neither test defines DLT replay/compensation policy or guarantees a human/operator notices the record.

### P0 — CSA-04A.10: DLT reconciliation and safe replay (**NEXT**)

**Scenario:** a conflict record is safely preserved in `.DLT`, but replaying it against an unchanged terminal Order will reproduce the same contradiction. Decide whether it represents an obsolete event, incorrect upstream status, duplicate business action, or a real payment/inventory inconsistency.

**Investigation gate:** define alert/ownership and record evidence (eventId, correlationId, orderId, topic/partition/offset, exception, timestamps); query authoritative Order/Payment/Inventory records before deciding compensation versus safe replay; preserve event identity and idempotency semantics. Document how to handle repeated DLT publication or recovery failure. Start with an operator runbook and targeted tests; do **not** auto-replay contradictory events or add a new service without evidence.

**Status:** ⬜ OPEN — no reconciliation implementation or validated runbook yet.

### P1 — Remaining Kafka listener/profile and failure-policy audit

- **Reviewed:** current `OrderEventListener` has no `@Retry`, `@CircuitBreaker`, `@Bulkhead` or fallback; `KafkaConsumerConfig` installs the handler. **Remaining:** inspect `application*.yml`, resilience profile and any other wrappers/configuration before claiming system-wide closure.
- Let retryable technical failures reach `DefaultErrorHandler`; verify DLT publication failure does not advance offsets (offset safety tests already exist).
- **Tested:** supported domain conflicts, unsupported types/versions and generic retryable failures have distinct handler paths. **Remaining:** representative production infrastructure exception subclasses, complete classification matrix, profiles, replay/reconciliation runbook.
- Re-examine Kafka event routing/deserialization and exact consumer-group semantics across shared inventory topic. Existing integration tests are evidence for covered cases, not an all-configuration guarantee.

### P1 — Public API and money/price correctness

- Validate `CreateOrderRequest` with appropriate non-null / positive constraints; ensure `OrderController` invokes Bean Validation and returns consistent error responses.
- **High-risk open issue:** client-provided price currently described in Phase 0 as the amount propagated toward payment. Require trusted price lookup or server-side price/amount verification before treating payment as production-ready. Do not add a pricing microservice by default.

### P2 — Code quality / contract ownership / observability

- Verify whether Lombok `@Data` still exists on `Order` and whether setters permit bypassing the state machine.
- Audit config ownership/bean names, `ObjectMapper` strategy, event version constants, unused `PaymentRequestedEvent`/`InventoryReserveRequestedEvent`, and DLT constants. Confirm usages before deleting or renaming anything.
- Add appropriate counters/alerts for Order lifecycle outcomes, Kafka lag, retries, DLT records, outbox backlog, publication failures, stale claims and recovery latency. Review correlation/eventId/orderId MDC propagation and cleanup.
- Test operator runbooks for outbox recovery, Kafka outage/consumer lag and DLT replay, including event ID preservation.

---

## 4. Phase 0 baseline — original findings retained for historical interpretation

The original audit described the following problems **at the time of capture**; they should not be copied into a fresh review as though they still describe running code:

- **03–04** `ProcessedEvent` save could precede business mutation, and `existsById`+`save` could race. **Now superseded** by transactional, atomic claim behavior.
- **05** separate valid event IDs for the same Order could race. **Now verified for tested paths** using `@Version`, domain rules and a real concurrent `OrderEventProcessor` integration test; losing transaction rolls back its claim and outbox.
- **06–07** database and Kafka publishing were separate, including creation. **Now superseded** by local transactional outbox. Relay publishing can still repeat after an uncertain send acknowledgment.
- **08–09** overlapping Resilience4j retry / circuit-breaker fallback risked hiding exceptions. **Reviewed listener is clean and propagates exceptions;** wider profiles/configuration remain to audit.
- **10** state changes previously allowed illegal terminal transitions. **Now fixed** by entity invariants.
- **11–13** broad JPA setters, weak input validation and client-authoritative price. **Not verified as fixed**.
- **14–16** event-type routing and failure classification. **CSA-04A.9 conflict classification and real Kafka DLT test GREEN;** broader producer/consumer profile audit and operational policies remain.
- **17–24** configuration, version/contract cleanup, DLT constants, and observability. **Still action items or monitoring**, subject to current source verification.

**New finding raised during Phase 1 (not explicit in Phase 0): `P1-ORDER-01 — Business-effect idempotency`.** Even with atomic claims by `eventId` and a legal no-op state transition, a second **different incoming event ID** for the same already-finalized outcome previously emitted another outgoing event. Reproduced with characterization GREEN → desired RED (two completion outbox events where only one expected) → minimal code fix → GREEN. Similar cancellation cases also GREEN. This distinction is now part of the canonical checklist.

**Additional Phase 1 finding: `P1-ORDER-02 / CSA-04A.9 — Retry of incompatible terminal outcome`.** After an optimistic conflict, a retry reading the opposite terminal state now throws `InvalidOrderStateTransitionException`, classified non-retryable and routed to `.DLT` in tested recovery scenarios. **Human/automated reconciliation and safe replay policy remains OPEN under `CSA-04A.10`.**

---

## 5. Source ownership / map for future audits

```text
order-service/src/main/java/com/kafka_implementation/order_service/
  controller/OrderController.java                # inbound REST adapter
  domain/Order.java                              # state machine + @Version
  domain/OrderStatus.java
  domain/InvalidOrderStateTransitionException.java # permanent conflicting lifecycle transition
  domain/CreateOrderRequest.java
  service/OrderService.java                      # transactional Order operations
  service/OrderEventProcessor.java               # claim -> transition -> outbox
  service/IdempotencyGuard.java                  # atomic processed_event claim
  consumer/OrderEventListener.java               # inbound Kafka adapter
  config/KafkaConsumerConfig.java
  config/KafkaProducerConfig.java
  config/ObjectMapperConfig.java
  repository/OrderRepository.java
  repository/ProcessedEventRepository.java
  outbox/domain/OutboxEvent.java
  outbox/repository/OutboxEventRepository.java
  outbox/service/OutboxWriter.java
  outbox/service/OutboxRelay.java
  outbox/service/OutboxClaimService.java
  outbox/service/OutboxKafkaPublisher.java
  outbox/service/OutboxPublicationStateService.java
  outbox/service/OutboxRecoveryService.java
  outbox/service/OutboxRecoveryScheduler.java
  resources/db/migration/V1__create_order_service_schema.sql
  resources/db/migration/V2__processed_event_timestamp_not_null.sql

order-service/src/test/java/com/kafka_implementation/order_service/
  service/OrderServiceIT.java                    # MySQL/Testcontainers regressions
  service/IdempotencyGuardTest.java
  domain/OrderTest.java
  consumer/OrderKafkaListenerIT.java
  consumer/OrderKafkaDltIT.java
  consumer/OrderKafkaDltOffsetSafetyIT.java
  config/KafkaDltRecoveryTest.java
  migration/FlywayChecksumIT.java
  migration/FlywayPartialMigrationIT.java
```

The directory tree shared on 29 October 2026 establishes these filenames, not the exact current contents of every file.

---

## 6. Next-session protocol — no duplicate investigations

1. **Read this section and the status matrix before suggesting code changes.** Reuse all existing tests; do not re-propose the old `existsById` or dual-write fixes.
2. Ask for current source **only for affected classes** if not already available, rather than another full module dump. For next item (`CSA-04A.10`), start with existing DLT operations/replay handling, topic/retention/alert configuration and the event reconciliation ownership model; do not re-audit already verified handler classification.
3. For each open finding use: **hypothesis → current source inspection → characterizing test if needed → RED invariant → smallest change → focused GREEN → full regression → audit status update**.
4. Record **what the test really proved**. Separate event-ID idempotency, same-business-outcome idempotency, aggregate concurrency, domain validity, DB/outbox atomicity and at-least-once Kafka publication.
5. At meaningful milestone completion, propose a concise Git commit message and update this file with commit hash if known. Do not claim a commit was executed by the assistant.
6. Keep a dated verification entry including exact command and suite count. A failing new test after the 31 October checkpoint does not invalidate the historical GREEN result, but must update the **current** status.

---

## 7. Architecture principles to preserve

- **Event-ID deduplication** answers whether a specific message was already handled.
- **Business-effect idempotency** answers whether a different message would redundantly apply/publish the same business outcome.
- **Domain state machine** rejects impossible transitions and allows deliberate same-terminal no-ops.
- **Optimistic locking** prevents two stale versions of an aggregate from both committing conflicting updates.
- **Transactional outbox** atomically persists local state and an *intent to publish*, not exactly-once Kafka delivery.
- **Kafka retry/DLT ownership** must allow exceptions to reach the infrastructure and distinguish recoverable failures from permanent conflicts; DLT preservation is not business reconciliation.
- **Kafka key ordering** is within a partition, not across distinct topics or consumer groups.
- Do not add a saga coordinator, 2PC, extra database, unnecessary producer abstraction, or new locking mechanism without evidence of need.

---

## 8. Change log

| Date | Change | Evidence |
|---|---|---|
| 13 Oct 2026 | Phase 0 `order-service.md` originally captured 26 audit observations, prioritized remediation and architecture lessons. | Original audit file supplied earlier. |
| By 29 Oct 2026 | Phase 1 introduced/verifiably exercised atomic processed-event claims, transactional outbox, domain state rules, JPA optimistic locking, relay/recovery tests and migration tests. | Code reviews and named Order Service tests discussed in audit sessions. |
| 29 Oct 2026 | `CSA-04A.8` identified distinct-event-ID duplicate lifecycle events; added transition-aware processor checks; verified completion and both same-type/cross-type cancellation cases. | GREEN/RED/GREEN workflow and new `OrderServiceIT` cases. |
| 29 Oct 2026 | Verified direct concurrent `COMPLETED` vs `CANCELLED` optimistic locking using two transactions. | `shouldAllowOnlyOneConcurrentTerminalTransitionToCommit`. |
| 29 Oct 2026 | Full suite verification after changes. | `57` Surefire + `65` Failsafe = `122` passed; `BUILD SUCCESS`. |
| 29 Oct 2026 | Refactored audit from Phase 0-only snapshot into living Phase 0 → Phase 1 status ledger. | This document. |
| 30 Oct 2026 | Completed `CSA-04A.8` processor-level concurrent transition verification with independent `OrderEventProcessor` calls; verified winning commit and losing claim/outbox rollback. | `shouldRollbackLosingClaimAndOutboxOnConcurrentTerminalTransitions` focused GREEN. |
| 30 Oct 2026 | Full Order Service clean regression after processor concurrency test. | 57 Surefire + 66 Failsafe = **123 passed**, zero failures/errors/skipped, `BUILD SUCCESS`. |
| 30 Oct 2026 | Closed CSA-04A.8 and promoted terminal-state conflicting-event recovery to next priority `CSA-04A.9`. | Audit status matrix and active backlog updated; policy remains OPEN. |
| 31 Oct 2026 | `CSA-04A.9` introduced `InvalidOrderStateTransitionException`, classified terminal contradictions as non-retryable, and added domain, processor, handler and real-Kafka DLT tests. | Named `OrderTest`, `OrderServiceIT`, `KafkaDltRecoveryTest`, `OrderKafkaDltIT` checks passed. |
| 31 Oct 2026 | Full Order Service clean regression and technical closure of `CSA-04A.9`; opened `CSA-04A.10` for operational reconciliation/safe replay. | **59 Surefire + 68 Failsafe = 127 passed**, zero failures/errors/skipped; `BUILD SUCCESS`. |
