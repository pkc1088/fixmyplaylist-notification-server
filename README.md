FixMyPlaylist - Notification Service
- A serverless microservice responsible for asynchronously sending email notifications when YouTube playlist recovery tasks are completed.

---
Overview
- This service is separated from the Main Server to ensure loose coupling and independent scalability. It acts as a Kafka Consumer that reads recovery.completed.event messages and sends formatted email reports to users.

---
Architecture & Tech Stack
- Language: Java 17
- Framework: Spring Boot 3.4.4
- Message Broker: Apache Kafka (Confluent Cloud)
- Deployment: Google Cloud Run, Google Cloud Scheduler
- Others: Spring Mail

---
Key Architecture Decisions
1. Serverless Manual Polling vs. @KafkaListener (Cost Optimization)
- Context: Traditional @KafkaListener requires a 24/7 running server (CPU always allocated), which causes unnecessary costs during idle times.
- Decision: Replaced @KafkaListener with a Manual Polling approach using KafkaConsumer.
- Result: A Cloud Scheduler triggers the endpoint daily. The service wakes up (Cloud Run scale-from-zero), connects to Kafka, consumes all unread messages synchronously, sends emails, commits the offset, and goes back to sleep ($0 cost).

2. Dead Letter Queue (DLQ) for Fault Tolerance 
- Context: If an email fails to send (e.g., malformed user data, SMTP server downtime), the loop throws an exception, failing the offset commit and causing a Poison Pill infinite loop in the next polling cycle.
- Decision: Implemented a DLQ pattern (recovery.dlq.event).
- Result: Failed messages are caught, safely pushed to the DLQ topic via KafkaTemplate, and the main consumer offset advances. This guarantees zero data loss and prevents system bottlenecks without requiring a heavy Database dependency.