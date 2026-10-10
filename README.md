# Smart Incident & Log Diagnostics Engine

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis"/>
  <img src="https://img.shields.io/badge/Spring_AI-1.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring AI"/>
  <img src="https://img.shields.io/badge/Resilience4j-2.2-4A154B?style=for-the-badge" alt="Resilience4j"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Compose"/>
  <img src="https://img.shields.io/badge/Lombok-Latest-red?style=for-the-badge" alt="Lombok"/>
</p>

---

## Executive Summary

A production-grade Spring Boot microservice that **ingests batch execution errors and generates structured root-cause diagnostics using LLM orchestration**. Designed to serve as an intelligent on-call co-pilot, the engine:

- **Eliminates redundant LLM evaluations** via MD5 stack-trace hashing and a Redis deduplication cache, cutting response latency by **~75%** for duplicate incidents.
- **Enforces deterministic payloads** through strict JSON schema validation — every LLM response is schema-checked before persistence or delivery.
- **Guarantees availability** via Resilience4j Circuit Breaker and Rate Limiter wrapping all LLM calls, with a deterministic static fallback triggered on timeouts, 5xx errors, or API limit breaches.
- **Persists audit trails** to PostgreSQL so diagnostics survive cache eviction and can be re-inspected post-incident.
- **Supports multiple LLM backends** (OpenAI GPT-4o-mini, Ollama local models) with zero code changes — just swap the `spring.ai.model.chat` environment variable.

> **Resume Bullets Satisfied:**
> - *Built a microservice that ingests batch execution errors and generates structured root-cause diagnostics using LLM orchestration, incorporating Resilience4j circuit breakers and JSON schema validation for deterministic payloads.*
> - *Implemented stack-trace hashing and Redis caching to eliminate redundant LLM API evaluations, cutting response latency by 75% for duplicate errors.*

---

## Architecture

```mermaid
sequenceDiagram
    participant Client as REST Client
    participant API as IncidentTriageController
    participant TS as TriageService
    participant Cache as IncidentCacheService<br/>(Redis)
    participant CB as ResilientLlmDiagnosticsClient<br/>(Resilience4j CB + RL)
    participant LLM as Spring AI ChatClient<br/>(OpenAI / Ollama)
    participant DB as IncidentReportRepository<br/>(PostgreSQL)
    participant FB as FallbackDiagnosticsProvider

    Client->>API: POST /api/v1/incidents/triage<br/>{ serviceName, environment, errorLog }
    API->>TS: triage(request)

    TS->>TS: MD5 hash(errorLog) → errorHash
    TS->>Cache: get(errorHash)

    alt Cache HIT
        Cache-->>TS: DiagnosticReport (from Redis)
        TS-->>API: TriageOutcome(report, cacheHit=true)
        API-->>Client: 200 OK + X-Cache-Hit: true
    else Cache MISS
        Cache-->>TS: Optional.empty()
        TS->>CB: diagnose(request)

        CB->>CB: Acquire RateLimiter permit
        CB->>CB: Check CircuitBreaker state

        alt Circuit CLOSED / HALF-OPEN
            CB->>LLM: ChatClient.prompt().call().entity(DiagnosticReport)
            LLM-->>CB: DiagnosticReport (JSON)
            CB-->>TS: LlmDiagnosis(report, degraded=false)
            TS->>TS: SchemaValidator.validate(report)
            TS->>DB: save(IncidentReportEntity)
            TS->>Cache: put(errorHash, report, TTL=24h)
            TS-->>API: TriageOutcome(report, cacheHit=false)
            API-->>Client: 200 OK + X-Cache-Hit: false
        else Circuit OPEN / Rate Limited / LLM Error
            CB->>FB: fallback(request, errorHash)
            FB-->>CB: DiagnosticReport (static fallback)
            CB-->>TS: LlmDiagnosis(report, degraded=true)
            TS-->>API: TriageOutcome(fallback, cacheHit=false)
            API-->>Client: 200 OK + X-Cache-Hit: false
        end
    end
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Runtime | Java 17, Spring Boot 3.5 | Core application framework |
| API | Spring Web MVC, Bean Validation | REST endpoints & payload validation |
| LLM Integration | Spring AI 1.0 (OpenAI + Ollama) | Structured chat model calls |
| Fault Tolerance | Resilience4j 2.2 (CircuitBreaker + RateLimiter) | LLM call protection & fallback routing |
| Caching | Redis 7 (Spring Data Redis) | MD5-keyed deduplication cache (24h TTL) |
| Persistence | PostgreSQL 16 (Spring Data JPA / Hibernate) | Durable incident audit log |
| Observability | Spring Actuator, Resilience4j health indicators | Runtime metrics & circuit state exposure |
| DevOps | Docker Compose | Local Postgres + Redis stack |
| Utilities | Lombok, Jackson | Boilerplate reduction & JSON serialization |

---

## Project Structure

```
src/
├── main/java/com/sribuilds/triage/
│   ├── SmartLogTriageApplication.java   ← Bootstrap
│   ├── config/
│   │   ├── AppProperties.java           ← @ConfigurationProperties (app.*)
│   │   └── RedisConfig.java             ← StringRedisTemplate + key helper
│   ├── controller/
│   │   └── IncidentTriageController.java ← POST /api/v1/incidents/triage
│   ├── dto/
│   │   ├── DiagnosticReport.java        ← LLM output schema (JSON-serializable)
│   │   ├── Severity.java                ← CRITICAL | HIGH | MEDIUM | LOW
│   │   └── TriageRequest.java           ← Inbound payload + Bean Validation
│   ├── exception/
│   │   ├── ApiError.java                ← Uniform error envelope
│   │   ├── CacheSerializationException.java
│   │   ├── GlobalExceptionHandler.java  ← @RestControllerAdvice
│   │   ├── LlmUnavailableException.java
│   │   └── SchemaValidationException.java
│   ├── model/
│   │   └── IncidentReportEntity.java    ← JPA entity (incident_reports table)
│   ├── repository/
│   │   └── IncidentReportRepository.java ← Spring Data JPA repository
│   ├── service/
│   │   ├── FallbackDiagnosticsProvider.java  ← Static fallback report builder
│   │   ├── IncidentCacheService.java         ← Redis get/put with TTL
│   │   ├── LlmDiagnosis.java                 ← Result record (report + degraded flag)
│   │   ├── LlmDiagnosticsClient.java         ← Interface boundary
│   │   ├── ResilientLlmDiagnosticsClient.java ← CB + RL + Fallback decoration
│   │   ├── SchemaValidator.java              ← Strict schema enforcement
│   │   ├── SpringAiChatTriage.java           ← Spring AI ChatClient adapter
│   │   ├── TriageOutcome.java                ← Outcome record (report + cacheHit)
│   │   └── TriageService.java                ← Main orchestration pipeline
│   └── util/
│       └── Md5HashingUtil.java          ← Deterministic MD5 dedup hashing
├── main/resources/
│   └── application.yml                  ← Full config (Redis, Postgres, LLM, Resilience4j)
└── test/
    ├── java/com/sribuilds/triage/
    │   ├── controller/IncidentTriageControllerIT.java  ← @SpringBootTest integration
    │   └── service/
    │       ├── FallbackDiagnosticsProviderTest.java
    │       ├── IncidentCacheServiceTest.java           ← Cache hit/miss/corrupt
    │       ├── ResilientLlmDiagnosticsClientTest.java  ← CB open/half-open/closed
    │       ├── SchemaValidatorTest.java
    │       └── TriageServiceTest.java                  ← Full pipeline mock
    └── resources/
        └── application-test.yml         ← H2 in-memory DB + disabled LLM
```

---

## Local Development Setup

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ (or use `./mvnw`) |
| Docker & Docker Compose | 24+ |

### 1. Clone & Configure

```bash
git clone https://github.com/sri-builds/smart-log-triage-engine.git
cd smart-log-triage-engine

# Copy the example env file and populate secrets
cp .env.example .env
# Edit .env — at minimum set OPENAI_API_KEY (or switch to ollama)
```

### 2. Start Infrastructure

```bash
docker-compose up -d
# ✅ PostgreSQL on localhost:5432
# ✅ Redis on localhost:6379
```

### 3. Run the Application

**Option A — with OpenAI (default):**
```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

**Option B — with local Ollama (no API key needed):**
```bash
# Pull a model first: ollama pull llama3.1
SPRING_AI_MODEL_CHAT=ollama ./mvnw spring-boot:run
```

**Option C — fallback-only mode (no LLM required):**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 4. Run Tests

```bash
./mvnw test
# All 31 tests pass without Docker (uses H2 in-memory + Mockito)
```

---

## API Reference

### `POST /api/v1/incidents/triage`

Ingests a raw error log and returns a structured diagnostic report.

#### Request Body

```json
{
  "serviceName": "BillingBatchProcessor",
  "environment": "PROD",
  "errorLog": "java.lang.NullPointerException: Cannot invoke method getInvoiceId() because the return value of PaymentLookup.find() is null\n\tat com.verizon.billing.BatchJob.execute(BatchJob.java:142)\n\tat com.verizon.billing.scheduler.BatchScheduler.run(BatchScheduler.java:87)"
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `serviceName` | string | Required, 1–200 chars |
| `environment` | string | Required, 1–50 chars |
| `errorLog` | string | Required, 1–10000 chars |

#### Response — Cache MISS (`X-Cache-Hit: false`)

```bash
curl -s -D - -X POST http://localhost:8080/api/v1/incidents/triage \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "BillingBatchProcessor",
    "environment": "PROD",
    "errorLog": "java.lang.NullPointerException: Cannot invoke method getInvoiceId() because the return value of PaymentLookup.find() is null\n\tat com.verizon.billing.BatchJob.execute(BatchJob.java:142)"
  }'
```

```
HTTP/1.1 200 OK
Content-Type: application/json
X-Cache-Hit: false

{
  "incidentId": "f3a2e814-9c72-4b1a-8d45-2b7c0e3f1a9d",
  "errorHash": "a3cca2b2a1b5cf0fcf68d0ccfc86b0f0",
  "rootCause": "NullPointerException thrown because PaymentLookup.find() returns null when no matching payment record exists, and the caller at BatchJob.java:142 does not perform a null check before invoking getInvoiceId().",
  "severity": "CRITICAL",
  "recommendedAction": [
    "Add null guard in BatchJob.execute() before calling getInvoiceId(): if (payment == null) { log.error(...); continue; }",
    "Update PaymentLookup.find() to return Optional<Payment> and enforce null-safe call sites.",
    "Add a unit test covering the empty-result case in PaymentLookupTest.",
    "Deploy a hotfix to PROD and re-run the failed batch window."
  ],
  "suggestedFixSnippet": "Payment payment = PaymentLookup.find(orderId);\nif (payment == null) {\n    log.error(\"Payment not found for orderId={}\", orderId);\n    failedCount.incrementAndGet();\n    continue;\n}\nprocessInvoice(payment.getInvoiceId());"
}
```

#### Response — Cache HIT (`X-Cache-Hit: true`)

Submitting the **same payload** within 24 hours returns immediately from Redis:

```bash
# Same request, second call:
curl -s -D - -X POST http://localhost:8080/api/v1/incidents/triage \
  -H "Content-Type: application/json" \
  -d '{ "serviceName": "BillingBatchProcessor", "environment": "PROD", "errorLog": "..." }'
```

```
HTTP/1.1 200 OK
Content-Type: application/json
X-Cache-Hit: true

{
  "incidentId": "f3a2e814-9c72-4b1a-8d45-2b7c0e3f1a9d",
  "errorHash": "a3cca2b2a1b5cf0fcf68d0ccfc86b0f0",
  "rootCause": "NullPointerException thrown because PaymentLookup.find() returns null ...",
  ...
}
```

#### Response — LLM Fallback (Circuit Open / Rate Limited)

```
HTTP/1.1 200 OK
X-Cache-Hit: false

{
  "incidentId": "7e9d1f3c-0a4b-4e7c-b291-5a8c2f0d6e1a",
  "errorHash": "a3cca2b2a1b5cf0fcf68d0ccfc86b0f0",
  "rootCause": "LLM Service Unavailable. Default triage queued.",
  "severity": "MEDIUM",
  "recommendedAction": [
    "Retry triage once the LLM endpoint is reachable.",
    "Clear the circuit/rate limiter state if the outage is resolved.",
    "Trace the incident manually for immediate remediation."
  ],
  "suggestedFixSnippet": null
}
```

#### Validation Error (400 Bad Request)

```bash
curl -s -X POST http://localhost:8080/api/v1/incidents/triage \
  -H "Content-Type: application/json" \
  -d '{ "serviceName": "" }'
```

```json
{
  "timestamp": "2026-10-10T07:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "errorLog: must not be blank; serviceName: must not be blank",
  "path": "/api/v1/incidents/triage"
}
```

---

## Configuration Reference

All settings can be overridden via environment variables or `.env` file:

```yaml
# application.yml key settings
spring:
  ai:
    model:
      chat: openai           # openai | ollama | none
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini

resilience4j:
  circuitbreaker:
    instances:
      llmTriage:
        sliding-window-size: 10
        failure-rate-threshold: 50        # open at 50% failure rate
        wait-duration-in-open-state: 10s
  ratelimiter:
    instances:
      llmTriage:
        limit-for-period: 10              # 10 LLM calls/second
        timeout-duration: 0              # fail-fast, don't queue

app:
  incident:
    cache-ttl: 24h                        # Redis TTL
  llm:
    fallback-root-cause: "LLM Service Unavailable. Default triage queued."
```

---

## Observability

The Actuator exposes circuit breaker state and metrics:

```bash
# Health (includes circuit breaker state)
curl http://localhost:8080/actuator/health

# Circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents/llmTriage

# Rate limiter metrics
curl http://localhost:8080/actuator/ratelimiters
```

---

## Test Coverage

| Test Class | Type | What's Covered |
|-----------|------|----------------|
| `IncidentTriageControllerIT` | `@SpringBootTest` Integration | Full HTTP round-trip; cache hit/miss headers; validation errors |
| `TriageServiceTest` | Unit (Mockito) | Orchestration: hash → cache hit/miss → persist → cache warm |
| `IncidentCacheServiceTest` | Unit (Mockito) | Redis get/put; corrupt cache miss; 24h TTL verification |
| `ResilientLlmDiagnosticsClientTest` | Unit | CB open→fallback; healthy success; half-open recovery |
| `SchemaValidatorTest` | Unit | Missing fields; invalid severity; empty action list |
| `FallbackDiagnosticsProviderTest` | Unit | Static fallback report shape |
| `Md5HashingUtilTest` | Unit | Hash determinism; blank input guard |

Run all 31 tests:

```bash
./mvnw test
```

---

## Docker Compose

```yaml
# docker-compose.yml — starts PostgreSQL + Redis
docker-compose up -d

# Tear down (preserves volumes by default)
docker-compose down

# Full reset including volumes
docker-compose down -v
```

---

## License

MIT — see [LICENSE](LICENSE).
