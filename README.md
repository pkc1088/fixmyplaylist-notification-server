# FixMyPlaylist - Notification Service

**FixMyPlaylist의 이벤트를 소비해 이메일 알림을 발송하는 서버리스·이벤트 기반 마이크로서비스**

---
## Overview

- 메인 서버와 완전히 분리된 서버리스 기반 이벤트 처리 서비스.
- 외부 API 개입 및 분산 시스템 환경에서 발생하는 메시지 중복과 누락 해결.
- 사용자에게 정확히 한 번의 이메일 발송 보장.
- 서버리스 환경의 운영 비용 최소화 장점을 유지하면서도, 안정적인 Kafka 컨슈밍 및 Metric 수집 방안 적용.

---
## Architecture & Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.4.4
- **Infra**: Google Cloud Run, Cloud SQL
- **Database**: MySQL
- **Persistence**: Spring Data JPA, JPQL, JDBC
- **Message Broker**: Apache Kafka (Confluent Cloud)
- **Monitoring**: Micrometer, Stackdriver, Google Cloud Monitoring
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

### 4. Serverless Metrics Collection & Cost Optimization

**Context:**
- 동적 인스턴스 생성·소멸이 반복되는 Serverless 환경 특성상, Prometheus와 같은 Pull 기반 모니터링 방식은 부적합.
- 트래픽이 없을 때 CPU가 스로틀링되는 요청 기반 정책을 유지하여 인프라 비용을 최소화하면서도, 안정적으로 커스텀·시스템 지표를 수집하는 방안이 필요.

**Decision**:
- Push 기반 아키텍처 전환: Micrometer Stackdriver Registry를 채택하여, 애플리케이션 내부에서 직접 Google Cloud Monitoring으로 지표를 Publish.
- 멀티 인스턴스 충돌 방지: 동일 Time Series 전송 빈도 제한을 고려하여, GCP 메타데이터 서버에서 고유 인스턴스 ID를 조회해 Common Tag로 주입.
- Startup Probe 웜업 전략: CPU가 보장되는 Startup Probe 단계에서 리소스 소모가 큰 초기 gRPC/TLS 통신 및 Metric Descriptor 생성을 선제적인 수행해 타임아웃을 방지.

**Result**:
- 초기 웜업 통신 이후 CPU 스로틀링 상태에서도 1분 주기의 메트릭이 안정적으로 전송됨을 확인.
- 인스턴스 교체 및 Scale-out 환경에서도 메트릭이 인스턴스별로 정상 집계되는 것을 검증.
- 간헐적인 유휴 상태로 인한 커넥션 강제 종료 및 전송 데드라인 초과 발생 시에도, 다음 Publish 주기에서 연결이 자동으로 재수립되고 메트릭 수집이 재개됨을 확인.

---
