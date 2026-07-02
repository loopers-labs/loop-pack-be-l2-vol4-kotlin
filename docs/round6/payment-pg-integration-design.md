# Round 6 — PG 결제 연동 & Resilience 설계

> 외부 PG 시스템과의 **비동기 결제** 연동을 추가하고, Timeout · CircuitBreaker · Fallback(+Retry) 으로
> 외부 장애가 내부 시스템으로 전파되지 않도록 보호한다.

## 1. 목표 / 범위

- commerce-api 에 **Payment 도메인 + PG 연동 + Resilience** 를 신규 추가한다.
- `pg-simulator` 는 수정하지 않는다. (연동 대상)
- Must-Have: Fallback, Timeout, CircuitBreaker / Nice-To-Have: Retry

## 2. 연동 대상(pg-simulator) 동작 요약

| 항목 | 내용 |
| --- | --- |
| `POST /api/v1/payments` | 헤더 `X-USER-ID` 필수. 요청 지연 100~500ms, **40% 즉시 실패(500)** |
| 응답 | 성공 시 `transactionKey` + status `PENDING` 즉시 반환 (**결제 미확정**) |
| 비동기 처리 | 1~5초 후 확정: 성공 70% / 한도초과 20% / 잘못된 카드 10% |
| 콜백 | 확정 시 `callbackUrl` 로 `TransactionInfo` POST (반드시 `http://localhost:8080` 시작) |
| 조회 | `GET /payments/{transactionKey}`, `GET /payments?orderId=` |

**핵심 전제**: *요청 성공 ≠ 결제 성공.* 결과는 **콜백 또는 조회**로만 확정된다.

## 3. 도메인 모델 — Payment (주문당 1건)

`domain/payment/`

```
PaymentModel (BaseEntity 상속)
  - orderId: Long              // 우리 주문 PK (Order 와 1:1)
  - userId: Long
  - cardType: CardType         // SAMSUNG / KB / HYUNDAI
  - cardNo: String
  - amount: Long               // Order.finalAmount 로 서버가 계산 (요청 바디에 금액 없음)
  - transactionKey: String?    // PG 발급 키. 요청 접수 후 저장
  - status: PaymentStatus      // PENDING / SUCCESS / FAILED
  - reason: String?            // 실패 사유 (한도초과/잘못된카드/시스템오류)
```

`PaymentStatus`: `PENDING`, `SUCCESS`, `FAILED`

상태 전이(도메인 메서드로 캡슐화):
- `assignTransactionKey(key)` : 요청 접수 성공 시 PENDING + key 보관
- `complete(reason)` : PENDING → SUCCESS
- `fail(reason)` : PENDING → FAILED
- 멱등성: 이미 SUCCESS/FAILED 인 건에 콜백/동기화가 또 들어와도 안전하게 무시(또는 동일 결과면 no-op)

> **CardType 매핑**: PG의 `CardType` 은 pg-simulator 패키지 소속이므로 commerce-api 에 **자체 CardType enum** 을 둔다.

## 4. 레이어 구성 (기존 패턴 준수)

```
interfaces/api/payment/
  PaymentV1Controller        POST /api/v1/payments, POST /api/v1/payments/callback
  PaymentV1Dto               요청/응답 DTO (orderId, cardType, cardNo)
application/payment/
  PaymentFacade              주문 검증 → PG 요청 → 결제건 저장, 콜백/동기화 처리
  PaymentInfo
domain/payment/
  PaymentModel, PaymentStatus, CardType, PaymentService, PaymentRepository(interface)
  PgClient (포트 인터페이스)   // 외부 PG 추상화
infrastructure/payment/
  PaymentRepositoryImpl + PaymentJpaRepository
  PgFeignClient(FeignClient) + PgClientImpl   // Resilience 적용 지점
```

- **HTTP 클라이언트**: Feign (`spring-cloud-starter-openfeign`)
- 의존성: `resilience4j-spring-boot3`, `spring-boot-starter-aop`, `spring-cloud-starter-openfeign`
  (버전은 `gradle.properties` 단일 소스. Spring Cloud BOM 이미 존재)

## 5. 흐름 설계

### 5.1 결제 요청 (`POST /api/v1/payments`)

```
Controller → PaymentFacade.pay(loginId, pw, orderId, cardType, cardNo)
  1. 사용자 인증
  2. 주문 조회 + 소유/상태(PENDING) 검증, amount = order.finalAmount
  3. Payment(PENDING) 저장   ← 트랜잭션 A (DB only)
  4. [트랜잭션 밖] PgClient.request(...)  ← 외부 호출 (Timeout/CB/Fallback 적용)
  5. 응답의 transactionKey 를 Payment 에 반영  ← 트랜잭션 B
```

> **트랜잭션 경계 원칙**: 외부 호출(4)을 DB 트랜잭션 안에 넣지 않는다. (커넥션 점유·전파 방지)

### 5.2 콜백 수신 (`POST /api/v1/payments/callback`, 8080)

```
PG → Controller(callback) → PaymentFacade.handleCallback(transactionKey, status, reason)
  1. transactionKey 로 Payment 조회
  2. status 에 따라 Payment.complete()/fail()  (멱등)
  3. SUCCESS 면 Order.pay(), FAILED 면 주문/재고/쿠폰 복구 정책 결정
```

### 5.3 복구 (콜백 유실 / 타임아웃 대비)

- 콜백이 안 와도 `GET /payments?orderId=` 또는 `/{transactionKey}` 로 PG 상태를 조회해 동기화.
- **요청이 Timeout 으로 실패했어도** PG엔 거래가 생성됐을 수 있으므로 → orderId 조회로 확인 후 반영.
- 수동 동기화 API + (선택) 스케줄러로 PENDING 장기 체류 건 주기 동기화.

## 6. Resilience 적용 (application.yml)

| 전략 | 대상 | 설정 의도 |
| --- | --- | --- |
| **Timeout** | PG 요청/조회 | connect 짧게, read 2~5s. 느린 응답이 스레드 점유 못 하게 |
| **CircuitBreaker** | PG 호출 | 실패율/느린호출 비율 임계 초과 시 Open → Fallback 직행 |
| **Fallback** | PG 호출 | 장애 시 "결제 대기" 로 안전 응답 → 내부 서비스는 정상 동작 |
| **Retry**(Nice) | 일시적 실패(500/타임아웃) | backoff + 최대횟수. CB 와 조합 |

- 핵심 설정: `failure-rate-threshold`, `slow-call-duration-threshold`, `slow-call-rate-threshold`,
  `wait-duration-in-open-state`, `permitted-number-of-calls-in-half-open-state`
- 조합 순서(Resilience4j): `Retry → CircuitBreaker → TimeLimiter` 통념을 따름.

## 7. 장애 시나리오 & 정합성

| # | 시나리오 | 처리 |
| --- | --- | --- |
| 1 | 요청 40% 즉시 실패 | Retry/CB 로 흡수, 끝내 실패 시 Fallback("결제 대기") |
| 2 | 요청 Timeout 인데 PG는 접수됨 | orderId 조회로 transactionKey 회수 후 PENDING 반영 |
| 3 | 콜백 유실 | 스케줄러/수동 동기화로 상태 복구 |
| 4 | 콜백 중복 수신 | Payment 상태 전이 멱등 처리 |
| 5 | CB Open 지속(PG 다운) | 신규 결제는 즉시 Fallback, 주문 흐름은 안 멈춤 |

## 8. 구현 순서 (TDD, 페이즈별 커밋)

1. Payment 도메인 모델/상태전이 — 단위 테스트 (Red→Green→Refactor)
2. PaymentRepository(Impl) + JPA
3. PgClient 포트 + Feign 구현 + Timeout 설정
4. PaymentFacade.pay + 결제 요청 API (통합 테스트)
5. 콜백 수신 API + 멱등 처리
6. Resilience(CircuitBreaker/Fallback) 적용 + 장애 테스트
7. (Nice) Retry, 동기화/복구 API

---

### 확정 사항

- **결제 최종 실패 시**: 주문은 **PENDING 유지 + 재결제 허용**. 재고/쿠폰은 원복하지 않는다.
  (재결제로 흐름을 이어가고, 주문 취소는 별도 유스케이스로 분리)
- **X-USER-ID**: `user.id` 를 문자열로 전달.
- **동기화 복구**: **수동 동기화 API + 스케줄러** 병행. PENDING 장기 체류 건을 주기적으로 PG 조회해 반영.
