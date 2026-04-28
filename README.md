# FixMyPlaylist - Notification Service

**FixMyPlaylist의 이벤트를 소비해 이메일 알림을 발송하는 서버리스·이벤트 기반 마이크로서비스**

---
## Overview

- 메인 서버와 완전히 분리된 서버리스 기반 이벤트 처리 서비스.
- 외부 API 개입 및 분산 시스템 환경에서 발생하는 메시지 중복과 누락 해결.
- 사용자에게 정확히 한 번의 이메일 발송 보장.

---
## Architecture & Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.4.4
- **Message Broker**: Apache Kafka (Confluent Cloud)
- **Deployment**: Google Cloud Run
- **Scheduler**: Google Cloud Scheduler
- **Security**: GCP IAM (OIDC Token)
- **Email**: Resend API (Prod), GMail (Test)
- **Template Engine**: Thymeleaf

---
## Architecture Design

### 1. Exactly-Once Delivery Strategy (Idempotency)
**Context**:
- Kafka 및 메인 서버의 At-Least-Once 특성으로 인해 메시지 중복 유입 가능.
- 일반 SMTP 기반 외부 API는 멱등성 키를 제공하지 않아 중복 발송 위험 존재.

**Decision**:
- `eventId`를 PK로 사용하는 Inbox 패턴을 통해 중복 이벤트 유입 차단.
- 멱등성 키를 제공하는 Resend API를 도입해 발송 단계의 멱등성 확보.

**Result**:
- 서버 장애, 오프셋 커밋 실패 등으로 인한 재처리 상황에서도 사용자에게 단 한 번의 이메일만 전달되도록 보장.


### 2. Resilience & Self-Healing (Retry Pipeline)
**Context**:
- 외부 API 장애 및 비정상 종료 상황에서 메시지 처리 누락 가능성 존재.

**Decision**:
- `FAILED`, `PENDING` 상태 이벤트를 재처리하는 스케줄러 기반 재시도 구조 설계.
- DLQ 기반 보완 파이프라인 구성.

**Result**:
- 일시적인 장애 상황에서도 데이터 유실 없이 발송 완료. 
- 관리자 개입 없이 자동 복구 가능한 구조 확보.


### 3. Cost & Resource Optimization (Serverless Manual Polling)

**Context**:
- `@KafkaListener` 기반 구조는 상시 실행 서버를 요구하여 유휴 비용 발생.

**Decision**: 
- KafkaConsumer 기반 수동 Polling 방식으로 전환.
- 스케줄러 트리거 기반 단기 실행 구조 채택.

**Result**: 
- 필요 시에만 컨테이너가 실행되는 서버리스 이벤트 프로세서 구축해 인프라 비용 최소화.

---
