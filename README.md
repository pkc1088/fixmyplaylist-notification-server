# FixMyPlaylist - Notification Service

**A serverless, event-driven microservice responsible for asynchronously processing YouTube playlist recovery events and dispatching email notifications.**

---
## Overview

- This service is fully decoupled from the Main Server, designed to operate exclusively within a serverless environment(Google Cloud Run).
- Instead of maintaining a continuous connection to the message broker(Kafka), it wakes up on-demand, consumes pending events, executes business logic, and elegantly terminates.
- It drives infrastructure costs down to zero during idle periods.

---
## Design Rationale

| Characteristic      | Implementation Choice                               | Justification & Technical Guarantee                                                                                                                                                                             |
|:--------------------|:----------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Loose Coupling**  | Kafka Topic(`recovery.completed.event`)             | The main server remains agnostic of notification delivery status. Downstream network anomalies are safely absorbed by Kafka's persistent log.                                                                   |
| **Fault Isolation** | Spring AOP `@Retryable` & DLQ(`recovery.dlq.event`) | Transient SMTP errors are mitigated via exponential backoff retries. Poison Pill messages are caught and routed to a Dead Letter Queue before manual offset commit, guaranteeing progression without data loss. |
| **Cost Efficiency** | Manual Polling over `@KafkaListener`                | Replaces the traditional `@KafkaListener` daemon. The container scales to zero, wakes via Cloud Scheduler, drains the topic until MAX_EMPTY_POLLS is reached, and shuts down safely.                            |
| **Maintainability** | Hexagonal Architecture (Ports & Adapters)           | Core domain logic is strictly isolated from framework-specific code via explicit interfaces, ensuring high testability and easy migration.                                                                      |

---
## Architecture & Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.4.4
- **Message Broker**: Apache Kafka(Confluent Cloud)
- **Deployment**: Google Cloud Run
- **Scheduler**: Google Cloud Scheduler
- **Security**: GCP IAM(OIDC Token)
- **Email**: Spring Mail
- **Template Engine**: Thymeleaf

---
## End-to-End Flow

```
[Google Cloud Scheduler]
    │ (1) Trigger via HTTPS with OIDC Token
    ▼
[GCP IAM / Identity-Aware Proxy] ──> Rejects unauthenticated external bots (403 Forbidden)
    │ (2) Token Validation Passed
    ▼
[NotificationController] ──> Validates X-Notification-Secret header
    │ (3) Invoke UseCase
    ▼
[KafkaMessagePullAdapter] ──> Manual poll() from 'recovery.completed' topic
    │ (4) Deserialize & Process
    ▼
[SmtpEmailAdapter] ──> Render Thymeleaf template & Send Mail (Max 3 Retries)
    │ (5) Success / Exhausted Retries
    ▼
[Offset Commit / KafkaDlqAdapter] ──> Commit sync or route to DLQ
```

---
## Architecture Decisions

### Cost & Resource Optimization (Serverless Manual Polling)

**Context**: 
- Traditional `@KafkaListener` requires a 24/7 running server (CPU always allocated), which causes unnecessary costs during idle times.

**Decision**: 
- Replaced `@KafkaListener` with a Manual Polling approach using KafkaConsumer.
- Implemented a `MAX_EMPTY_POLLS = 2` threshold. The service explicitly terminates its internal loop when the Kafka topic is drained, allowing the Cloud Run instance to spin down natively.

**Result**: 
- Achieved a 100% serverless event processor. The service incurs zero infrastructure costs outside the daily scheduled execution window.


### Fault Tolerance & Zero Data Loss Strategy

**Context**: 
- Failures in third-party SMTP servers or malformed data can cause infinite processing loops (Poison Pill) if offsets are not managed carefully.

**Decision**: 
- Applied Spring AOP `@Retryable(maxAttempts = 3, backoff = 3000ms)` to the `EmailPort` to handle transient network anomalies.
- Designed a custom `EventProcessor` callback. If retries are exhausted, the payload and exception stack are routed to the DLQ(Dead Letter Queue).

**Result**: 
- The consumer successfully commits the offset and advances to the next record, ensuring system bottlenecks are prevented while preserving failed events for manual inspection.