# Observability Layer — Architecture Audit

**Project:** `kafka-microservices`  
**Scope:** Prometheus, Grafana, Alertmanager, Zipkin, Kafka Exporter, application metrics/tracing/logging consistency  
**Status:** Phase 0 complete  
**Audit date:** 07 October 2026\
**Phase 1 principle:** Standardize and prove the existing telemetry before adding more technology.

## Executive Summary

The project already has a substantial observability foundation: Prometheus, Grafana, Alertmanager, Zipkin, Kafka Exporter, Spring Boot Actuator, Prometheus metrics, Kafka observation, distributed tracing dependencies, contextual logs, and initial alert rules.

The main problem is not “missing monitoring.” The components have grown as partially independent pieces rather than one coherent observability architecture.

Target mental model:

```text
                         OBSERVABILITY
                              |
          +-------------------+-------------------+
          |                   |                   |
       METRICS              TRACES               LOGS
          |                   |                   |
     Prometheus             Zipkin          central backend
          ^                                       ^
          |                                       |
   Kafka Exporter                          application logs
          |
          +------------------+
                             v
                          Grafana
                             |
                       dashboards/querying
                             |
                       Prometheus rules
                             |
                        Alertmanager
                             |
                         notification
```

Centralized logging is a requirement to evaluate in Phase 1. Loki + Grafana is a candidate, not a predetermined technology choice.

---

# Positive Findings — KEEP

### KEEP-01 — Prometheus is already the metrics backend
It scrapes Eureka, Gateway, the four business services and Kafka Exporter and has persistent storage.

### KEEP-02 — Grafana exists and persists state
Keep it, but make its datasource and dashboards reproducible from Git.

### KEEP-03 — Alertmanager is wired to Prometheus
Keep the architecture; repair delivery/configuration and validate it end-to-end.

### KEEP-04 — Distributed tracing infrastructure exists
Keep distributed tracing as a capability, but standardize the tracing implementation.

### KEEP-05 — Business services expose Actuator/Prometheus
Order, Payment, Inventory and Notification expose `health`, `info`, `metrics`, and `prometheus`, with health probes enabled.

### KEEP-06 — Application metric identity exists
The reviewed business services tag metrics with `${spring.application.name}`.

### KEEP-07 — HTTP histograms are explicitly enabled
All four reviewed business services enable `http.server.requests` percentile histograms.

### KEEP-08 — Kafka observation is substantially present
Order, Payment and Inventory explicitly enable producer and consumer observation. Notification enables consumer observation and has an additional Kafka observation property that needs standardization.

### KEEP-09 — Logs already contain useful workflow context
Across services the logs contain useful combinations of `service`, `traceId`, `spanId`, `correlationId`, `eventId`, `orderId`, `paymentId`, and `notificationType`.

### KEEP-10 — Prometheus and Grafana data are persisted
Named volumes are already present.

---

# Findings

## OBS-01 — `monitoring` no longer describes the layer accurately
**Severity:** Low / Structural  
**Classification:** IMPROVE

The layer now spans metrics, alerting, dashboards, tracing and eventually centralized logs.

**Phase 1:** Rename `setup/3.monitoring` to something such as `setup/3.observability` while doing the structural refactor, not during Phase 0.

## OBS-02 — Zipkin and Kafka Exporter are physically in the Platform Compose layer
**Severity:** Medium  
**Classification:** IMPROVE

Logically, Eureka/Gateway are platform components while Zipkin/Kafka Exporter are observability components.

**Phase 1:** Evaluate moving Zipkin and Kafka Exporter into the Observability Compose layer while preserving the shared network.

## OBS-03 — Observability image versioning is inconsistent
**Severity:** Medium  
**Classification:** IMPROVE

Prometheus, Zipkin and Kafka Exporter use `latest` while Grafana and Alertmanager are pinned.

**Phase 1:** Pin deliberate versions and upgrade intentionally.

## OBS-04 — Grafana has no repository-provisioned Prometheus datasource
**Severity:** High  
**Classification:** FIX

Grafana state persists, but no datasource provisioning was shown.

**Risk:** A clean clone may require manual UI setup.

**Phase 1:** Provision the Prometheus datasource from version-controlled configuration.

## OBS-05 — Grafana dashboards are not version controlled/provisioned
**Severity:** Medium  
**Classification:** IMPROVE

No dashboard provisioning/dashboard JSON was shown.

**Phase 1:** Create a small purposeful dashboard set: system overview, JVM/runtime, Kafka/event processing, and database visibility where justified.

## OBS-06 — Grafana credentials are hardcoded `admin/admin`
**Severity:** High  
**Classification:** FIX

Fine only as a temporary local shortcut.

**Phase 1:** Parameterize credentials. Do not introduce a large secrets platform solely for this local problem.

## OBS-07 — Alertmanager SMTP configuration is placeholder/hardcoded
**Severity:** High  
**Classification:** FIX

Values such as `devops@eu.com`, `smtp.eu.com`, and `SECRET_PASSWORD` indicate that real delivery is not demonstrated.

**Phase 1:** Make delivery environment-driven and test a real alert from rule evaluation through notification and resolution.

## OBS-08 — No consistent secret strategy exists for observability configuration
**Severity:** Medium  
**Classification:** IMPROVE

**Phase 1:** Use environment/appropriate secret injection for the project's maturity. Never commit real SMTP/admin secrets.

## OBS-09 — Prometheus targets are static
**Severity:** Review  
**Classification:** KEEP LOCALLY / REASSESS FOR DEPLOYMENT

Static Docker service names are simple and appropriate for the current local Compose topology.

**Decision:** Do not add dynamic service discovery without a concrete need.

## OBS-10 — `ServiceDown` applies the same semantics to every target
**Severity:** Medium  
**Classification:** IMPROVE

`up == 0` includes Java applications and infrastructure exporters.

**Phase 1:** Improve labels/annotations/grouping so application and infrastructure failures are operationally clear.

## OBS-11 — HTTP error-rate alert aggregates all services together
**Severity:** High  
**Classification:** FIX

The current numerator/denominator lose service identity.

**Failure scenario:** A low-volume service can fail completely while healthy high-volume traffic keeps the global error percentage below the threshold.

**Phase 1:** Calculate error rate per job/application.

## OBS-12 — HTTP P95 alert aggregates all services together
**Severity:** High  
**Classification:** FIX

The histogram query retains `le` but loses service/job identity.

**Phase 1:** Preserve job/application when aggregating histogram buckets.

## OBS-13 — HTTP histogram support is confirmed for all four reviewed business services
**Severity:** Informational / Verify remaining runtimes  
**Classification:** KEEP + VERIFY

Order, Payment, Inventory and Notification explicitly enable the histogram needed for HTTP percentile calculations.

**Remaining:** Verify Gateway/Eureka behavior if they are included in the same P95 alert.

## OBS-14 — Current “SLO” rules are threshold alerts, not a complete SLO/error-budget model
**Severity:** Medium  
**Classification:** IMPROVE

A mature model distinguishes SLI, SLO, error budget and burn rate.

**Phase 1:** Keep current rules as a stepping stone. Define a few meaningful objectives before introducing burn-rate alerts.

## OBS-15 — Kafka lag thresholds are arbitrary
**Severity:** Medium  
**Classification:** IMPROVE

Warning `>50` and critical `>500` are not yet tied to throughput, recovery time or user impact.

**Phase 1:** Derive thresholds through controlled load/failure testing.

## OBS-16 — Kafka warning/critical timing needs an explicit rationale
**Severity:** Review  
**Classification:** IMPROVE

Warning lasts 2m; critical lasts 5m. This can be valid, but should be intentional and documented.

## OBS-17 — Observability components are not comprehensively self-monitored
**Severity:** Medium  
**Classification:** IMPROVE

The shown scrape config does not include Prometheus itself, Alertmanager or Grafana.

**Phase 1:** Add useful self-monitoring without creating unnecessary recursive complexity.

## OBS-18 — Health/restart policy differs across observability containers
**Severity:** Medium  
**Classification:** IMPROVE

Alertmanager has `restart: unless-stopped`; equivalent behavior/healthchecks are not consistently shown for Prometheus/Grafana.

**Phase 1:** Define one deliberate local runtime policy.

## OBS-19 — Centralized log aggregation is missing
**Severity:** High architectural gap  
**Classification:** ADD AFTER DESIGN

Current flow ends at Docker/stdout/manual inspection.

**Phase 1:** Evaluate a lightweight searchable log backend. Loki is a candidate because Grafana already exists, but the requirement is correlation/queryability, not a specific brand.

## OBS-20 — Logs are contextual but not machine-structured
**Severity:** Medium  
**Classification:** IMPROVE

The pattern is human-readable key/value-like text.

**Phase 1:** Evaluate structured JSON logging together with the selected ingestion architecture.

## OBS-21 — JVM-specific observability is not represented in reviewed dashboards/rules
**Severity:** Medium  
**Classification:** IMPROVE

Useful signals include heap, GC, threads, CPU, process memory and connection-pool utilization.

**Phase 1:** Build a small Java runtime dashboard using existing Micrometer metrics and use it as a JVM-learning exercise.

## OBS-22 — Database/MySQL observability is missing
**Severity:** Medium  
**Classification:** IMPROVE

This matters particularly for Inventory because correctness relies on database transactions/locking.

**Phase 1:** Start with datasource/Hikari application metrics. Add a MySQL exporter only if server-level visibility is actually needed.

## OBS-23 — Kafka visibility is primarily consumer-lag focused
**Severity:** Medium  
**Classification:** IMPROVE / LATER

Kafka Exporter + lag alerts are valuable, but richer broker/JVM health was not demonstrated.

**Phase 1:** Add JMX/broker telemetry only if failure engineering shows existing signals cannot answer important questions.

## OBS-24 — Five-second scrape/evaluation intervals are aggressive
**Severity:** Review  
**Classification:** KEEP LOCALLY IF INTENTIONAL

Useful for fast local feedback but increases metric work/storage.

**Phase 1:** Retain or adjust based on actual local resource use and detection requirements.

## OBS-25 — Brave and OpenTelemetry tracing bridges coexist
**Severity:** High  
**Classification:** FIX

Order, Payment, Inventory and Notification all contain both tracing paths, plus Zipkin-related reporting/export dependencies.

**Risk:** There is no single authoritative tracing pipeline and dependency/configuration complexity is unnecessarily high.

**Phase 1:** Choose one project-wide Micrometer tracing bridge and remove the competing path.

## OBS-26 — Zipkin configuration is not standardized across applications
**Severity:** High / Verify  
**Classification:** FIX

Business services use a Zipkin property shape different from the one observed in Eureka.

**Phase 1:** Verify the property hierarchy against the actual Spring Boot/Micrometer stack, standardize it, then prove end-to-end span export.

## OBS-27 — Trace sampling is hardcoded to 100%
**Severity:** Medium  
**Classification:** IMPROVE

100% is useful locally but should not silently become a production policy.

**Phase 1:** Make sampling environment-aware.

## OBS-28 — Base `application.yml` files contain Docker topology
**Severity:** High configuration-design issue  
**Classification:** FIX

Business configurations contain Docker hostnames such as `mysql-db`, `kafka`, `eureka-server`, and `zipkin`, while Compose activates `docker` but no meaningful `application-docker.yml` separation exists.

**Phase 1 target:**

```text
application.yml
    -> common/environment-neutral behavior

application-docker.yml
    -> Docker-specific endpoints/defaults

environment variables/secrets
    -> deployment overrides
```

## OBS-29 — `application-resilience.yml` loading is not demonstrated
**Severity:** High / Verify  
**Classification:** FIX

The file exists in Order, Payment, Inventory and Notification, but the reviewed base configuration does not demonstrate import/profile activation.

**Risk:** The repository may describe resilience policies that are not actually active.

**Phase 1:** Explicitly import/activate or consolidate the configuration, then prove runtime behavior.

## OBS-30 — Current health/SLO model is too HTTP-centric for an event-driven architecture
**Severity:** High conceptual issue  
**Classification:** IMPROVE

Payment, Inventory and Notification perform their important business work through Kafka, while current latency/error rules focus on HTTP.

**Phase 1:** Add low-cardinality event-driven signals such as processing duration, processing outcomes, retry exhaustion, DLT activity, throughput, consumer lag and possibly end-to-end workflow duration.

Never use IDs such as `orderId`, `eventId`, `paymentId` or `correlationId` as Prometheus labels.

## OBS-31 — Resilience4j is not visibly integrated into observability
**Severity:** Medium  
**Classification:** IMPROVE

Retries/circuit breakers/bulkheads exist in configuration, but corresponding operational metrics/dashboards are not shown.

**Phase 1:** First settle retry ownership and prove configuration loading; then expose/use actionable resilience metrics.

## OBS-32 — Logback service-name resolution differs across services
**Severity:** Medium  
**Classification:** FIX

Observed:
- Order: environment variable + fallback
- Payment: Spring property
- Inventory: environment variable + incorrect fallback
- Notification: Spring property

**Phase 1:** Define one convention.

## OBS-33 — Inventory can identify itself as `order-service` in logs
**Severity:** High  
**Classification:** FIX

Inventory contains `${SPRING_APPLICATION_NAME:-order-service}`.

**Risk:** Centralized log searches can become actively misleading.

**Phase 1:** Correct immediately when implementation begins, then eliminate the wider configuration drift.

## OBS-34 — Log context has no documented common schema
**Severity:** Medium  
**Classification:** IMPROVE

Suggested conceptual split:

```text
COMMON
service
traceId
spanId
correlationId
eventId
orderId (where workflow-wide)

DOMAIN-SPECIFIC
Payment -> paymentId
Inventory -> productId/inventory identifier when useful
Notification -> notificationType
```

Business IDs belong naturally in logs; do not automatically turn them into metric labels.

## OBS-35 — Notification Kafka observation configuration differs
**Severity:** Medium  
**Classification:** FIX / STANDARDIZE

Order/Payment/Inventory explicitly configure producer and consumer observation. Notification uses a different general property plus consumer observation, with no equivalent producer block shown.

**Phase 1:** Determine whether Notification produces records. If consumer-only, document it intentionally. Standardize on one supported configuration mechanism.

## OBS-36 — Notification is less environment-parameterized than the other business services
**Severity:** Medium  
**Classification:** FIX

Notification directly hardcodes application/Kafka/Eureka/Zipkin values while the other services more often provide environment-overridable expressions.

**Phase 1:** Bring Notification under the same profile/environment strategy.

## OBS-37 — Notification HTTP histogram is not its primary business-health signal
**Severity:** Low / Design review  
**Classification:** REVIEW

HTTP metrics remain useful for Actuator, but Notification's principal workload is Kafka event processing.

**Phase 1:** Keep useful HTTP telemetry while defining event-processing health as the primary operational signal.

## OBS-38 — Cross-cutting observability configuration is heavily duplicated and has drifted
**Severity:** Medium  
**Classification:** IMPROVE

Evidence includes duplicate tracing stacks, Logback differences, Inventory's wrong fallback, Notification Kafka observation drift, differing parameterization, and differing Zipkin configuration.

**Phase 1:** Standardize conventions first. Do not immediately create a large custom observability framework/shared library.

## OBS-39 — Alertmanager routing/grouping policy is minimal
**Severity:** Medium  
**Classification:** IMPROVE

One receiver handles the shown alert set and no deliberate severity/service routing strategy was demonstrated.

**Phase 1:** After cleaning the alert taxonomy, introduce only the grouping/routing required by the project.

## OBS-40 — Alertmanager persistence is not shown
**Severity:** Low/Medium  
**Classification:** IMPROVE

Prometheus/Grafana persist state; equivalent Alertmanager state persistence was not shown.

**Phase 1:** Persist it if silences/runtime state are expected to survive container recreation.

## OBS-41 — Operational UIs are host-exposed without a cloud access policy
**Severity:** Low locally / Higher for deployment  
**Classification:** KEEP LOCALLY, FIX BEFORE CLOUD

Prometheus, Grafana, Alertmanager, Zipkin and similar operational surfaces are convenient to expose locally.

**Cloud phase:** Treat them as internal operational surfaces and restrict/authenticate access appropriately.

## OBS-42 — Automated observability configuration validation is not demonstrated
**Severity:** Medium  
**Classification:** IMPROVE

A Maven build does not validate Prometheus/Alertmanager configuration semantics.

**Phase 1/CI:** Add lightweight native configuration/rule validation where practical.

---

# Cross-Service Matrix

| Capability | Order | Payment | Inventory | Notification |
|---|---|---|---|---|
| Actuator | Yes | Yes | Yes | Yes |
| Prometheus registry | Yes | Yes | Yes | Yes |
| Prometheus endpoint | Yes | Yes | Yes | Yes |
| Health probes | Yes | Yes | Yes | Yes |
| Application metric tag | Yes | Yes | Yes | Yes |
| HTTP histogram | Yes | Yes | Yes | Yes |
| Kafka consumer observation | Yes | Yes | Yes | Yes |
| Kafka producer observation | Yes | Yes | Yes | Review/not demonstrated |
| Brave bridge | Yes | Yes | Yes | Yes |
| OTel bridge | Yes | Yes | Yes | Yes |
| Sampling | 100% | 100% | 100% | 100% |
| traceId/spanId logs | Yes | Yes | Yes | Yes |
| correlationId/eventId logs | Yes | Yes | Yes | Yes |
| orderId logs | Yes | Yes | Yes | Yes |
| Domain-specific context | orderId | paymentId | limited | notificationType |
| Correct/consistent service log identity | Yes | Different mechanism | **Wrong fallback** | Different mechanism |
| Machine-structured logs | No | No | No | No |
| Central log backend | No | No | No | No |
| Docker profile separation | No | No | No | No |
| Resilience config proven active | No | No | No | No |

---

# Phase 1 Action Plan — Recommended Order

## 1. Standardize profiles/configuration
Create a clear `application.yml` + `application-docker.yml` + environment override strategy. Prove resilience configuration loading.

## 2. Choose one tracing stack
Select one Micrometer tracing bridge, remove the competing path, standardize Zipkin configuration and prove cross-Kafka trace propagation.

## 3. Standardize logging
Fix Inventory's service name, define common MDC/context fields and one service identity mechanism.

## 4. Make Grafana reproducible
Provision Prometheus datasource and a small version-controlled dashboard set.

## 5. Correct Prometheus rules
Scope error rate/P95 by service, improve target semantics, and verify every query against real exported metrics.

## 6. Add event-driven metrics
Measure the Kafka workload rather than treating every microservice as primarily HTTP-driven.

## 7. Integrate Resilience4j metrics
Only after configuration and retry/circuit-breaker ownership are understood.

## 8. Add centralized logging
Evaluate structured stdout + collector + searchable backend. Loki/Grafana is a candidate architecture, not a mandatory answer.

## 9. Add JVM/runtime visibility
Use heap, GC, threads, CPU, memory and Hikari metrics as both operational signals and a JVM-learning exercise.

## 10. Improve database visibility
Start application-side; add server-level MySQL telemetry only when useful.

## 11. Expand Kafka broker visibility only if failure tests justify it
Kafka Exporter/lag already solve a real problem. Add JMX telemetry only for unanswered operational questions.

## 12. Evolve alerts into meaningful SLIs/SLOs
Learn and apply SLI, SLO, error budget and burn rate incrementally.

## 13. Make alert delivery real
Parameterize Alertmanager and prove delivery/resolution.

## 14. Add observability validation to CI
Validate Prometheus rules/config and Alertmanager configuration independently of the Maven build.

---

# Failure-Engineering Exercises for Phase 1

### Kill Payment Service
Observe `up`, Eureka state, consumer lag, alerting, logs and backlog recovery.

### Stop Kafka temporarily
Observe producer/consumer behavior, retry ownership, circuit-breaker behavior, logs/traces and recovery.

### Slow event processing
Observe consumer lag, processing duration, threads, database pool and thresholds.

### Force a controlled processing exception
Observe retries, idempotency, eventual DLT behavior, correlation identifiers and alert usefulness.

### Create database pressure
Observe Hikari pool behavior, transaction latency, Inventory locking, JVM threads and Kafka lag.

The key operational question should become:

> What signal tells me there is a problem, where do I investigate next, and can I correlate metrics, traces and logs?

---

# Technology Decisions

| Capability | Current decision |
|---|---|
| Prometheus | KEEP |
| Grafana | KEEP |
| Alertmanager | KEEP |
| Zipkin | KEEP for current architecture; standardize tracing |
| Kafka Exporter | KEEP |
| Centralized logs | ADD capability in Phase 1 |
| Loki | CANDIDATE, not mandatory |
| Structured JSON logs | EVALUATE with aggregation |
| MySQL exporter | LATER / only if needed |
| Kafka JMX exporter | LATER / only if needed |
| Full SLO/error-budget model | LEARN + introduce incrementally |
| Enterprise secrets platform | DON'T ADD without need |
| Large shared observability framework | DON'T ADD prematurely |

---

# Target Phase 1 Architecture

```text
                    Java/Spring Services
                           |
             +-------------+-------------+
             |             |             |
          METRICS        TRACES         LOGS
             |             |             |
         Micrometer     Micrometer     Logback
             |             |             |
         Prometheus    ONE bridge     structured stdout
             ^             |             |
             |             v             v
      Kafka Exporter     Zipkin       collector
             |                           |
             |                       log backend
             |                    (Loki candidate)
             |                           |
             +-------------+-------------+
                           v
                         Grafana
                           |
                    Prometheus rules
                           |
                      Alertmanager
                           |
                    real notification
```

# Phase 0 Verdict

**Maturity: GOOD FOUNDATION / NOT YET COHERENT AS ONE OBSERVABILITY SYSTEM**

The project is well beyond having “no monitoring.” Its largest weaknesses are configuration/tracing drift, non-reproducible Grafana setup, missing centralized logs, HTTP-centric health signals for event-driven workloads, globally aggregated HTTP alerts, limited JVM/database/resilience visibility, inconsistent logging conventions, unverified resilience-file loading, and placeholder alert-delivery/security configuration.

The Phase 1 goal is therefore **not to install many more tools**. It is to make the existing pieces work as one operational system:

```text
detect with metrics
        |
locate with traces
        |
explain with logs
        |
correlate through shared context
        |
alert only when action is meaningful
```
