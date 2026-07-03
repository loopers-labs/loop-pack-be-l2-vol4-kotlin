# Round 7 — 이벤트 기반 아키텍처 (ApplicationEvent → Kafka → 선착순 쿠폰) 설계

- 작성일: 2026-07-03
- 브랜치: `volume-7`
- 범위: Round 7 Step 1(ApplicationEvent) + Step 2(Kafka + Outbox) + Step 3(선착순 쿠폰). 통합 설계 1개, 구현은 A→B→C 순차.

---

## 1. 목표

이벤트 기반 아키텍처의 **Why → How → Scale** 을 관통한다.

- **Why**: 무거운 트랜잭션·높은 결합도를 트랜잭션 분리로 완화. 주 로직/부가 로직 경계를 판단한다.
- **How**: 시스템 간 전파가 필요한 이벤트를 Kafka로 발행(Outbox로 at-least-once), Consumer가 멱등하게 집계.
- **Scale**: 선착순 쿠폰 발급을 Kafka 버퍼 + 순차 소비 + 수량 동시성 제어로 구현.

학습 포인트는 "이걸 이벤트로 분리해야 하는가"의 **판단 기준** 자체다. 무조건 분리가 아니라 근거 있는 분리.

## 2. 현재 코드베이스 상태 (탐색 결과)

**재사용 (이미 있음):**
- 좋아요→집계가 이미 `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` + 원자적 JPQL 증감(`ProductLikeCountEventHandler`, `LikeEvent`). Step 1 패턴의 살아있는 예시.
- 결제가 이미 주문 tx에서 분리됨(volume-6): `CreateOrderUsecase`(재고+쿠폰+주문저장만), `RequestPaymentUsecase`(PG 호출 tx 밖), `SyncPaymentResultUsecase`(CAS).
- 비동기 결과 폴링 레퍼런스: payment `PENDING`→reconcile 패턴.
- 동시성 3종: 비관락(`ProductStockJpaRepository.findByProductIdForUpdate`), 낙관락 `@Version`(`UserCouponModel`), CAS(`PaymentJpaRepository.compareAndSetStatus`). `CountDownLatch` 테스트 지원(`ConcurrencyTestSupport`), 쿠폰 동시성 테스트 존재.
- Kafka 인프라 뼈대: `modules/kafka`의 `KafkaConfig`(`KafkaTemplate<Any,Any>`, batch listener 팩토리 `AckMode.MANUAL`, concurrency 3), streamer의 `DemoKafkaConsumer` 템플릿, `docker/infra-compose.yml`에 실제 broker.

**Greenfield (신규):**
- commerce-api → Kafka **프로듀서** (commerce-api는 `modules:kafka` 의존·`kafka.yml` import 둘 다 없음).
- `kafka.yml`에 `acks`/`enable.idempotence` 미설정(기본값만).
- **Transactional Outbox** (테이블 + 릴레이). outbox `event_id`용 UUID (BaseEntity는 auto-increment Long뿐).
- streamer **Consumer**: `product_metrics` upsert + `event_handled` 멱등 테이블.
- **선착순 수량** 개념 (쿠폰에 수량 제한 전무 — 1인1매 유니크 제약만).
- **Kafka Testcontainers** 설정 (현재 MySQL/Redis만).
- 유저 행동(조회) 이벤트, 알림 스텁 핸들러.
- (참고) `commerce-streamer` `application.yml`의 `spring.application.name: commerce-api` 오타 → `commerce-streamer`로 정정.

## 3. 설계 원칙

1. **핵심 tx 최소화** — 주문/좋아요/쿠폰요청은 커밋만 빠르게, 부가·전파 로직은 커밋 이후로.
2. **내부 이벤트 vs 시스템간 이벤트 분리** — 한 JVM 내 후속은 `ApplicationEvent`, 시스템 간 전파는 Kafka. 같은 도메인 사실을 둘 다 소비할 수 있다.
3. **At-least-once 발행 + 멱등 소비** — Outbox로 발행 보장, `event_handled`로 중복 무해화, upsert로 동일 결과.
4. **순서는 파티션 키로** — `productId`/`orderId`/`couponId` 기준 파티셔닝. 파티션 단위 순서만 보장됨을 전제.

## 4. 확정된 결정 (기본값, spec 리뷰에서 재검토 가능)

| # | 결정 | 채택 | 대안 |
|---|---|---|---|
| D1 | Outbox Relay 위치 | **commerce-api `@Scheduled`** (프로듀서 자기완결, ~1s) | commerce-batch 잡 (부하분리, 단 상시 스케줄링 필요) |
| D2 | 선착순 수량 모델 | **`CouponModel`에 `total_quantity`/`issued_count` 필드** | 별도 `CouponStockModel` (ProductStock 패턴) |
| D3 | `@Async` 범위 | **로깅·알림 스텁만 비동기**, 집계는 동기 AFTER_COMMIT 유지 | 전부 동기 |
| D4 | 이벤트 로그 테이블 | **`event_handled`만 필수**, 원본 로그 테이블은 Nice-to-have | 둘 다 구현 |

## 5. 아키텍처

```
commerce-api (Producer)
  ├─ 도메인 tx: 비즈니스 저장 + (BEFORE_COMMIT 리스너가) Outbox row 기록  ← 같은 tx, 원자적
  ├─ ApplicationEvent 내부 후속(AFTER_COMMIT):
  │     ├─ ProductLikeCountEventHandler (집계, 동기, 기존 유지)
  │     ├─ UserActionLogEventHandler     (유저행동 로깅, @Async)
  │     └─ NotificationEventHandler      (알림 스텁, @Async)
  └─ OutboxRelay (@Scheduled ~1s): PENDING outbox → KafkaTemplate.send → SENT
        ├─ catalog-events         key=productId  (LikeChanged, ProductViewed)
        ├─ order-events           key=orderId    (OrderCreated/판매)
        └─ coupon-issue-requests  key=couponId   (CouponIssueRequested)

commerce-streamer (Consumer, manual ack)
  ├─ MetricsConsumer(catalog-events, order-events)
  │     → event_handled 멱등 체크 → product_metrics upsert (version/updated_at 최신만)
  └─ CouponIssueConsumer(coupon-issue-requests, key=couponId → 쿠폰별 순차)
        → event_handled 멱등 → 원자적 수량차감 → UserCoupon 발급 or REJECTED
        → coupon_issue_request 상태 갱신(PENDING→ISSUED/REJECTED)
```

**단일 발행 지점**: 각 usecase는 `ApplicationEvent`를 한 번 발행한다. `@TransactionalEventListener(BEFORE_COMMIT)` 리스너가 그 이벤트를 Outbox row로 직렬화(같은 tx, 비즈니스 데이터와 원자적). `AFTER_COMMIT` 리스너들은 내부 후속을 처리. 이렇게 usecase를 Outbox 기술 세부에서 분리한다.

## 6. Step A — ApplicationEvent 경계 (내부, Kafka 없음)

- **유저행동 이벤트**: `ProductViewedEvent`(신규, 상품 상세 조회 usecase), 기존 `LikeCreated/DeletedEvent`, `OrderCreatedEvent`(신규 — 주문 도메인에 이벤트 없음, 주문/판매). 각 usecase 커밋 지점에서 발행.
- **좋아요→집계**: 기존 `ProductLikeCountEventHandler` **그대로 유지**(AFTER_COMMIT, REQUIRES_NEW, 원자적 UPDATE). 집계 실패와 무관하게 좋아요는 성공. `products.like_count`는 API 자체 정렬/조회용(병존).
- **알림 스텁**: `NotificationEventHandler` 신규. 주문/결제 완료 이벤트 구독, 실제 전송 없이 로그/기록만. @Async + 필요 시 REQUIRES_NEW. "주 로직 vs 부가 로직" 경계 시연.
- **유저행동 로깅**: `UserActionLogEventHandler` 신규. 조회/좋아요/주문 이벤트를 서버 로그로 적재. @Async.
- **@Async 설정**: `AsyncConfig`(executor) 신규. 예외 은닉 방지 위해 `AsyncUncaughtExceptionHandler`/로깅 포함.

**리스너 phase 매핑**
| 관심사 | phase | tx | 비동기 |
|---|---|---|---|
| 집계(like_count) | AFTER_COMMIT | REQUIRES_NEW | 동기 |
| Outbox 기록 | BEFORE_COMMIT | 원 tx 참여 | 동기 |
| 유저행동 로깅 | AFTER_COMMIT | 불필요/REQUIRES_NEW | @Async |
| 알림 스텁 | AFTER_COMMIT | REQUIRES_NEW | @Async |

## 7. Step B — Kafka + Outbox

**Outbox 테이블** `outbox_event`
- `id`(PK, auto), `event_id`(UUID, unique — consumer 멱등 키), `aggregate_type`, `aggregate_id`, `topic`, `partition_key`, `payload`(JSON), `status`(PENDING/SENT), `created_at`, `sent_at?`.
- 비즈니스 저장과 **같은 tx**에 기록(BEFORE_COMMIT 리스너).

**Relay** (`OutboxRelay`, commerce-api `@Scheduled` ~1s)
- `findTop-N-ByStatusOrderByIdAsc(PENDING)` → `KafkaTemplate.send(topic, partitionKey, payload)` → 성공 콜백에서 `SENT`(또는 삭제). 실패는 다음 주기 재시도 → at-least-once.
- `@Scheduling` 활성화(`@EnableScheduling`).

**Producer config** (`kafka.yml`)
- `acks: all`, `enable.idempotence: true`(→ retries, max.in.flight 자동 정합), key=`StringSerializer`, value=`JsonSerializer`.
- commerce-api `build.gradle.kts`에 `modules:kafka` 추가, `application.yml`에 `kafka.yml` import.

**Consumer (streamer)**
- `product_metrics(product_id PK, like_count, sales_count, view_count, version 또는 updated_at)`. **upsert** (`INSERT ... ON DUPLICATE KEY UPDATE` 또는 조회 후 저장).
- 멱등: 메시지 처리 전 `event_handled(event_id PK)` insert 시도 → 이미 있으면 skip. 처리 성공 후 커밋(manual ack). 증분 카운터(like/sales/view += 1)의 이중 반영 방지는 이 `event_handled`가 주 방어.
- 최신성(`version`/`updated_at`): 절대값 상태를 싣는 이벤트에 적용 — 수신 이벤트의 `occurredAt`/`version`이 `product_metrics.updated_at`/`version`보다 과거면 반영 안 함. 순수 증분 카운터엔 멱등만으로 충분하나, 스키마·비교 로직은 최신성 요구(재집계·스냅샷 이벤트)에 대비해 `updated_at` 컬럼으로 둔다.
- manual Ack: `AckMode.MANUAL`, 처리 완료 후 `acknowledgment.acknowledge()`.
- streamer가 `commerce-api` 도메인을 참조하지 않도록, Kafka payload는 자체 DTO로 역직렬화(도메인 결합 회피).

**Kafka Testcontainers**: `modules/kafka` testFixtures에 `KafkaTestContainersConfig`(bootstrap-servers system property 주입) 신규. producer→consumer 왕복 통합테스트에 사용.

## 8. Step C — 선착순 쿠폰 발급

**수량 모델** (D2): `CouponModel`에 `total_quantity: Int`, `issued_count: Int` 추가.

**요청 API** `POST /api/v1/coupons/issue`
- `coupon_issue_request(id, request_id UUID, user_id, coupon_id, status=PENDING, created_at)` 저장 + Outbox 기록(같은 tx) → 즉시 `requestId` 반환(빠른 응답, 실제 발급 X).

**발급 Consumer** (`CouponIssueConsumer`, topic `coupon-issue-requests`, key=`couponId`)
- 쿠폰별 **단일 파티션 순차 처리** → 동일 쿠폰 요청은 직렬화.
- 처리 순서:
  1. `event_handled` 멱등 체크.
  2. 중복 발급 방지: `(user_id, coupon_id)` 이미 발급이면 REJECTED(DUPLICATE).
  3. **원자적 조건부 UPDATE**: `UPDATE coupons SET issued_count = issued_count + 1 WHERE id = :id AND issued_count < total_quantity`. affected=1 → 발급 진행, affected=0 → REJECTED(SOLD_OUT).
  4. 성공 시 `UserCouponModel` 발급 저장, `coupon_issue_request` → ISSUED. 실패 시 → REJECTED(사유).
- 단일 파티션 순차 + 원자적 UPDATE 이중 안전. (파티션 하나라도 원자적 UPDATE로 초과발급 0 보장)

**비동기 결과** `GET /api/v1/coupons/issue/{requestId}`
- `coupon_issue_request` 상태 폴링(PENDING/ISSUED/REJECTED + 사유). payment PENDING→result 패턴 재사용.

**동시성 테스트**: `ConcurrencyTestSupport.runConcurrently`로 N(예: 1만) 동시 요청 → 정확히 `total_quantity` 장, 초과발급 0, 중복발급 0 검증.

## 9. 새 스키마 (JPA ddl-auto 자동생성, 마이그레이션 파일 없음)

| 테이블 | 소유 앱 | 핵심 컬럼 |
|---|---|---|
| `outbox_event` | commerce-api | event_id(UUID uniq), topic, partition_key, payload, status |
| `product_metrics` | commerce-streamer | product_id(PK), like_count, sales_count, view_count, version/updated_at |
| `event_handled` | commerce-streamer | event_id(PK), handled_at |
| `coupon_issue_request` | commerce-api | request_id(UUID uniq), user_id, coupon_id, status, reason |
| `coupons` (+컬럼) | commerce-api | total_quantity, issued_count |

- prod는 `ddl-auto: none` → 실제 스키마 생성은 out-of-band(마이그레이션 도구 부재). 이번 과제는 local/test(`create`) 범위로 진행, prod 반영은 리스크로 명시.

## 10. 에러 / 실패 처리

- **발행 실패**: Outbox 재시도로 at-least-once. Relay는 idempotent producer + `event_id`로 중복 무해.
- **소비 실패**: 재시도 backoff. 반복 실패 메시지 → **DLQ 격리**(Nice-to-have). Lag 모니터링.
- **예외 은닉 방지**: @Async 리스너에 uncaught 핸들러/로깅.
- **`event_handled` ↔ 로그 분리(설계노트)**: `event_handled`는 멱등 dedup 전용(핫패스, 처리키만, 보존기간 후 prune 가능). 원본 이벤트 로그는 감사·재처리·디버깅용(append-only, 장기 보존). 관심사·수명·접근패턴이 달라 분리. 본 과제는 `event_handled`만 필수(D4).

## 11. 테스트 전략

- 각 Step TDD(Red→Green→Refactor), 3A.
- **A**: 리스너 phase/트랜잭션 경계 단위·통합 테스트(집계 실패해도 좋아요 성공, AFTER_COMMIT 발화, Outbox row가 같은 tx에 기록).
- **B**: Kafka Testcontainers 왕복. 멱등(같은 event_id 두 번 → 한 번만 반영), 순서(파티션 키), Outbox at-least-once(발행 실패→재시도).
- **C**: 선착순 동시성(초과발급 0, 중복 0), 비동기 결과 폴링, 매진 REJECTED.

## 12. 구현 순서 (A→B→C)

- **A**: 유저행동 이벤트 + 로깅/알림 스텁 핸들러 + @Async 설정. (기존 집계 유지)
- **B**: Outbox(테이블+BEFORE_COMMIT 기록+Relay) → commerce-api Kafka 프로듀서(acks/idempotence) → streamer MetricsConsumer(product_metrics upsert + event_handled) → Kafka Testcontainers.
- **C**: 쿠폰 수량 필드 → 요청 API(+Outbox) → CouponIssueConsumer(순차+원자적 차감) → 결과 폴링 API → 동시성 테스트.

각 Step 후 `analyze-query`/`analyze-concurrency`/`analyze-external-integration` 스킬로 점검.

## 13. 미결 / 리스크

- D1~D4 기본값은 spec 리뷰에서 재검토 가능.
- prod 스키마 마이그레이션 경로 부재(ddl-auto none) — 과제 범위 밖으로 둠.
- `commerce-streamer`가 `commerce-api` 도메인에 결합되지 않도록 payload DTO 경계 유지 필요.
- Consumer 배치 처리·Consumer Group 분리·DLQ는 Nice-to-have(시간 허락 시).
