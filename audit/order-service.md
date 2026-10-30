# Order Service — Architecture Audit & Implementation Ledger

**Repository:** `kafka-microservices` · **Module:** `order-service`  
**Origin:** Phase 0 architecture audit (13 October 2026)  
**Reconciled through:** Phase 1, CSA-04A.8 checkpoint (29 October 2026)  
**Status:** Phase 0 audit complete; Phase 1 **in progress**; last full Order Service regression **GREEN: 122/122**  
**Document purpose:** The single entry point for what was found, what was implemented, what was verified, and what must be done next. Do not treat old Phase 0 observations as descriptions of the current code without consulting the current-status column.

> **Evidence convention:** `VERIFIED` means confirmed by code shared in the review and/or a named successful test. `IMPLEMENTED / REVIEW` means an implementation is discussed or visible but its precise current production configuration was not re-audited in this reconciliation. `OPEN` means not shown resolved. `PARTIAL` means some aspects are verified but others remain. `BASELINE` refers to the October 13 Phase 0 snapshot, not necessarily the current source tree. Never turn a proposed design or passing test into a broader untested guarantee.

---

## 0. Restart here — current status in one minute

- **Next active item:** `CSA-04A.8` — *processor-level concurrent transaction test*. The direct JPA optimistic-lock race is already GREEN; the full race through `OrderEventProcessor` with `processed_event` and `outbox_event` atomicity is **NOT YET TESTED**.
- **Last full verification (29 Oct 2026):** `./mvnw.cmd -pl order-service -am clean verify` (Windows PowerShell: `.\mvnw.cmd`) — **57 unit tests + 65 integration tests = 122 passed**, zero failures, errors, skipped; reactor BUILD SUCCESS. This is the last confirmed complete suite, not a promise about later edits.
- **Current verified improvements:** transactional event claim, at-most-once business transition for sequential duplicate outcomes, domain state machine, optimistic version checking, transactional outbox for creation and lifecycle events, outbox recovery/fencing tests, DLT/offset safety test coverage.
- **Not established:** end-to-end exactly-once delivery (not claimed); complete processor-level concurrent rollback semantics; handling the losing conflicting event on retry; authoritative price validation; resolution of every Phase 0 configuration/security/serialization item.
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

**Important:** the above reflects reviewed application flow. Kafka topic processing order across different topics is not globally guaranteed. Incoming events that conflict with a committed terminal outcome need an explicit policy; no policy is asserted completed here.

---

## 1. Phase 0 finding-to-Phase 1 status matrix

**Status meanings:** ✅ Verified resolved within stated scope · 🟡 Partial / verify remaining cases · ⬜ Open / not evidenced · 📌 Keep/monitor.

| Phase 0 ID | Finding (preserved) | Current status | Verified evidence / remaining action |
|---|---|---|---|
| 03 | Consumer idempotency transaction boundary | ✅ **VERIFIED** | `OrderEventProcessor` transactional methods; `IdempotencyGuard` has `MANDATORY` propagation. `OrderServiceIT` tests rollback and claim reuse. |
| 04 | Idempotency `exists`→`insert` race | ✅ **VERIFIED** | Atomic `INSERT IGNORE` to unique `processed_event.event_id`, strict warning classification; concurrent claim and cross-transaction tests; `IdempotencyGuardTest` warning tests. |
| 05 | Different events race for same Order | 🟡 **PARTIAL** | `@Version` + domain transition rules; real MySQL simultaneous `COMPLETED` vs `CANCELLED` entity test GREEN. **Actual processor + claim/outbox race still pending.** |
| 06 | Lifecycle DB/Kafka dual write | ✅ **VERIFIED** (DB/outbox atomicity) | Transactional outbox in Order Service, lifecycle-event rollback tests and outbox relay tests. **Kafka relay remains at-least-once.** |
| 07 | Initial Order creation dual write | ✅ **VERIFIED** | Creation persists Order + `OrderCreatedEvent` outbox atomically; rollback test when outbox writing fails. |
| 08 | Overlapping listener retry mechanisms | ⬜ **REVIEW CURRENT CONFIG** | Phase 0 saw Resilience4j `@Retry` plus Kafka handler. Do not assert removed without current listener/config inspection. |
| 09 | Circuit-breaker fallback can swallow failures | 🟡 **PARTIAL** | DLT publication-failure recovery and Kafka offset-safety tests GREEN. Still inspect actual listener annotations/fallback and error propagation for all paths. |
| 10 | Unrestricted Order state machine | ✅ **VERIFIED** | `CREATED -> COMPLETED/CANCELLED`; same-terminal no-op; opposite-terminal throws; `OrderTest`, integration tests. |
| 11 | JPA `@Data` / entity encapsulation | ⬜ **VERIFY SOURCE** | Original risk persists unless `Order.java` accessors were deliberately constrained; no closure evidence captured. |
| 12 | API request validation | ⬜ **OPEN / VERIFY** | Validate non-null IDs, positive quantity/price, `@Valid` and controller error contract; no verified closure. |
| 13 | Client controls payment price | ⬜ **OPEN — HIGH RISK** | Do not accept client-supplied price as authoritative charge amount; decide trusted pricing policy/owner. |
| 14 | Serializer / type-header mismatch | 🟡 **PARTIAL** | Inventory serialization and routing unit tests GREEN; full producer/consumer configuration consistency still requires audit. |
| 15 | Multiple listeners on shared inventory topic | 🟡 **PARTIAL** | `OrderEventRoutingTest`, `OrderKafkaListenerIT` GREEN; confirm routing/consumer-group assumptions under production config. |
| 16 | Failure classification | 🟡 **PARTIAL** | DLT, malformed/unsupported event and offset-safety coverage exists; complete classification table and Kafka retry policy still to confirm. |
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
| **Business-effect idempotency** | Domain transition returns `boolean transitioned`; `OrderService.complete/cancel` propagate it; `OrderEventProcessor` stores lifecycle outbox event only if `transitioned=true`; incoming distinct event ID still claimed when same outcome already committed. | Characterization GREEN (two completion events), desired RED (expected one, got two), fixed GREEN `shouldCreateOnlyOneCompletionEventForDistinctIncomingEventIds`; `shouldCreateOnlyOneFailureEventForDistinctPaymentFailureEventIds`; `shouldNotCreateAnotherFailureEventWhenPaymentFailsAfterInventoryFailure`. | ✅ Verified sequential cases (29 Oct) |
| Kafka DLT/offset resilience | Spring Kafka `DefaultErrorHandler`/recoverer tested for failure and retry behavior, including DLT publication failure and offset safety. | `KafkaDltRecoveryTest`, `OrderKafkaDltIT`, `OrderKafkaDltOffsetSafetyIT`, `OrderKafkaListenerIT`. | ✅ Test evidence; review retry-annotation policy |
| Schema migrations | Flyway migration scripts `V1__create_order_service_schema.sql` and `V2__processed_event_timestamp_not_null.sql`; MySQL Testcontainers migration tests. | `FlywayChecksumIT`, `FlywayPartialMigrationIT`, full suite. | ✅ Verified migration test coverage |

### Business-effect idempotency: exact tested contract

```text
1) Incoming eventId already claimed       -> processor returns false; no side effect.
2) New eventId, Order CREATED             -> claim; transition; one lifecycle outbox event; return true.
3) New eventId, same already terminal state -> claim; transition returns false; no new lifecycle outbox; return true.
4) New eventId, incompatible terminal state -> exception; transaction rolls back claim and side effects.
```

**Contract note:** `OrderEventProcessor`'s boolean reports that the incoming event was newly accepted/handled (`true`) versus duplicate event ID (`false`); it does **not** mean a state transition occurred. The internal `transitioned` boolean carries that distinction. Preserve this when refactoring listeners or tests.

**Consequence:** A distinct late failure event after a completed order, or late success after a cancelled order, is not a harmless same-state repeat. The domain currently rejects it. Kafka retry/DLT and reconciliation treatment of these **incompatible outcomes** remains an explicit open policy question; do not silently treat them as completed work.

### Test outcomes — latest verified full regression

Executed from repository root in Windows PowerShell on **29 October 2026**:

```powershell
$log = Join-Path $env:TEMP "order-csa-04a8-verify.log"
.\mvnw.cmd -pl order-service -am clean verify *> $log
Select-String -Path $log -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE|Reactor Summary'
```

| Suite | Count | Outcome |
|---|---:|---|
| Surefire unit tests | 57 | All PASS |
| Failsafe integration tests | 65 | All PASS |
| **Combined** | **122** | **0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |

`OrderServiceIT`: **51** passing integration tests, including the four new CSA-04A.8 tests (distinct completion, distinct payment failure, cross-type failure, simultaneous entity state transition). Other integration tests: Kafka DLT IT 3, DLT offset safety IT 1, Kafka listener IT 4, Flyway checksum IT 1, Flyway partial migration IT 1, Order repository IT 4 (14 outside OrderServiceIT; total 65).

The `ERROR` log lines in `KafkaDltRecoveryTest` for simulated broker failures are expected **test-injected failures**, not suite failures; judge outcome by the test summaries.

**Do not use `122 passed` to claim that the full processor-level concurrency invariant has been verified.** That test is the next item.

---

## 3. Active engineering backlog — ranked, not already completed

### P0 — CSA-04A.8: processor-level concurrent transaction atomicity (**NEXT**)

**Goal:** Reproduce overlapping processing of two different events on the same Order with realistic transactional boundaries (e.g., inventory success vs payment failure), not merely overlapping direct entity modifications.

**Required assertions after both transactions settle:**

- Only one terminal Order state committed (`COMPLETED` or `CANCELLED`).
- Exactly **one** corresponding terminal lifecycle event committed to `outbox_event` (ignore `ORDER_CREATED` in the count).
- Winning incoming `eventId` persisted in `processed_event`.
- Losing incoming `eventId` **not** persisted if its transaction failed and rolled back.
- A deliberately observed losing optimistic-lock failure, not an accidental serialized execution, timeout, unrelated error, or unsupported state transition.
- Retry/replay policy for the loser is analyzed separately. Under current domain rules, a retry encountering the opposite terminal state throws; determine whether to classify as non-recoverable, reconcile, or investigate an upstream invariant violation. Do not default to silent acknowledgement.

**Test design:** two independent transactions, barriers at a test-scoped seam around the Order loading/transition stage, real `OrderEventProcessor` and `OutboxWriter`; keep production code free of test-only latches. Review existing `OrderServiceIT` test helpers before building new scaffolding. Verify MySQL transaction/flush order, and ensure a test does not deadlock by holding a shared key lock before reaching the barrier.

**Status:** ⬜ Not implemented/verified as of this checkpoint. **Gate to close CSA-04A.8:** focused processor race GREEN + full regression GREEN + documented losing-message policy or explicit deferred action.

### P1 — Kafka failure semantics and listener configuration

- Inspect current `OrderEventListener`, `KafkaConsumerConfig`, `application*.yml` and resilience profile; establish whether `@Retry`, `@CircuitBreaker`, `@Bulkhead` or fallbacks still wrap listener execution. The original Phase 0 snapshot observed overlapping retry and exception-swallowing risk; do not present these as current code facts until inspected.
- Let retryable technical failures reach `DefaultErrorHandler`; verify DLT publication failure does not advance offsets (offset safety tests already exist).
- Distinguish transient infrastructure failures, unsupported/deserialization errors, permanent business conflicts, and expected business failures. Decide retry limits/backoff/DLT mapping and document DLT replay/reconciliation.
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
- **05** separate valid event IDs for the same Order could race. **Partially addressed** via `@Version` and domain rules, with processor-level concurrency still pending.
- **06–07** database and Kafka publishing were separate, including creation. **Now superseded** by local transactional outbox. Relay publishing can still repeat after an uncertain send acknowledgment.
- **08–09** overlapping Resilience4j retry / circuit-breaker fallback risked hiding exceptions from Kafka error handlers. **Needs current configuration audit**, not automatically closed by passing DLT tests.
- **10** state changes previously allowed illegal terminal transitions. **Now fixed** by entity invariants.
- **11–13** broad JPA setters, weak input validation and client-authoritative price. **Not verified as fixed**.
- **14–16** event-type routing and failure classification. **Some coverage exists**, but design-wide audit remains.
- **17–24** configuration, version/contract cleanup, DLT constants, and observability. **Still action items or monitoring**, subject to current source verification.

**New finding raised during Phase 1 (not explicit in Phase 0): `P1-ORDER-01 — Business-effect idempotency`.** Even with atomic claims by `eventId` and a legal no-op state transition, a second **different incoming event ID** for the same already-finalized outcome previously emitted another outgoing event. Reproduced with characterization GREEN → desired RED (two completion outbox events where only one expected) → minimal code fix → GREEN. Similar cancellation cases also GREEN. This distinction is now part of the canonical checklist.

**Additional Phase 1 risk to track: `P1-ORDER-02 — Retry of incompatible terminal outcome`.** After an optimistic conflict, reprocessing against the now-finalized opposite outcome can throw a domain exception. The correct permanent/transient classification and reconciliation policy is **OPEN**, even though `@Version` prevents lost updates.

---

## 5. Source ownership / map for future audits

```text
order-service/src/main/java/com/kafka_implementation/order_service/
  controller/OrderController.java                # inbound REST adapter
  domain/Order.java                              # state machine + @Version
  domain/OrderStatus.java
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
2. Ask for current source **only for affected classes** if not already available, rather than another full module dump. For the next item, relevant files are `OrderServiceIT` concurrency helpers, `OrderEventProcessor`, `OrderService`, `OrderRepository`, transaction settings, and outbox repository/test setup.
3. For each open finding use: **hypothesis → current source inspection → characterizing test if needed → RED invariant → smallest change → focused GREEN → full regression → audit status update**.
4. Record **what the test really proved**. Separate event-ID idempotency, same-business-outcome idempotency, aggregate concurrency, domain validity, DB/outbox atomicity and at-least-once Kafka publication.
5. At meaningful milestone completion, propose a concise Git commit message and update this file with commit hash if known. Do not claim a commit was executed by the assistant.
6. Keep a dated verification entry including exact command and suite count. A failing new test after the 29 October checkpoint does not invalidate the historical GREEN result, but must update the **current** status.

### Suggested intermediate checkpoint (not claimed executed)

```bash
git status
git diff --stat
git add order-service
# review staged changes before commit
git diff --cached --stat
git commit -m "fix(order): enforce lifecycle business idempotency and verify concurrency"
```

Prefer committing related code + tests together; exclude generated `target/` files and temporary logs.

---

## 7. Architecture principles to preserve

- **Event-ID deduplication** answers whether a specific message was already handled.
- **Business-effect idempotency** answers whether a different message would redundantly apply/publish the same business outcome.
- **Domain state machine** rejects impossible transitions and allows deliberate same-terminal no-ops.
- **Optimistic locking** prevents two stale versions of an aggregate from both committing conflicting updates.
- **Transactional outbox** atomically persists local state and an *intent to publish*, not exactly-once Kafka delivery.
- **Kafka retry/DLT ownership** must allow exceptions to reach the infrastructure and distinguish recoverable failures from permanent conflicts.
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

