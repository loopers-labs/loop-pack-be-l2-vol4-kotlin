# Round 6 — PG 결제 연동 + Resilience (Week 6 계획)

> 작성: 2026-06-22 · 관련 분석: [pg-simulator-analysis.html](../pg-simulator-analysis.html), [pg-simulator-responses.md](../pg-simulator-responses.md)

## Context

commerce-api에 **PG 카드 결제** 기능을 추가하고, 외부 시스템(pg-simulator)의 **지연·실패·결과 미확정**에
대응하는 Resilience를 설계·적용한다.

- **Must-have**: Fallback / Timeout / CircuitBreaker
- **Nice-to-have**: Retry
- **핵심**: 정상 경로가 아니라 **실패·미확정 경로를 안전하게 처리**. PG 장애가 서비스 전체로 번지지 않게
  보호하고, 결과를 확신 못 받은 결제건을 **조회로 맞춰(reconcile)** 정합성을 회복한다.

### 이미 깔려 있는 것 (재사용)
- `OrderStatus`: `PENDING_PAYMENT → PAID/FAILED/UNKNOWN` 전이 규칙 + `canTransitionTo` (`order/domain/OrderStatus.kt`)
- `Order.confirmPayment()/failPayment()/markUnknown()` (`order/domain/Order.kt:81-92`, `transitionTo` 검증)
- `OrderFacade.place()`에 **"PG 연동 시 외부 호출은 Tx 밖으로 분리"** 주석 선반영 (`OrderFacade.kt:21`)
- 인증: `@RequestAttribute(ACCOUNT_ID)` (헤더 `X-Loopers-LoginId/Pw` → AccountHeaderAuthenticationFilter)
- 에러: 도메인 `ErrorCode` enum + `ApiControllerAdvice` 자동 매핑 / 응답: `ResponseBodyAdvice` 자동 래핑
- Spring Cloud BOM 2024.0.1 → openfeign / resilience4j-spring-boot3 바로 사용 가능
- `commerce-batch`(Spring Batch), `commerce-streamer`(Kafka) 앱 존재 (단 web/HTTP client·payment 접근 없음)

### 없는 것 (이번에 추가)
- `payment` 패키지 (Order엔 transactionKey 필드 없음)
- commerce-api에 resilience4j / HTTP client 의존성 0
- 외부 호출 테스트 도구(MockWebServer 등) 사용 이력 없음

---

## 확정된 설계 결정

| # | 결정 | 근거 |
|---|------|------|
| D1 | **별도 Payment 도메인** (payment 패키지가 transactionKey/status 보관) | bounded-context-first, 결제 이력·재조회 표현 자연스러움. Order는 전이 메서드로만 연결 |
| D2 | **FeignClient + resilience4j, 명시적 어노테이션 스타일** | plain `@FeignClient`(HTTP만) + 서비스에 `@CircuitBreaker(fallbackMethod=...)`. Timeout은 Feign `readTimeout`. resilience 동작이 코드에 드러남 |
| D3 | **Fallback 시 주문 = UNKNOWN** | PG에선 결제됐을 수 있어 FAILED 단정 금지. `markUnknown()` 후 조회로 확정 → 중복결제·정합성 사고 방지 |
| D4 | **결제는 비동기 접수 모델** (PG '제출'까지만 동기, PENDING 즉시 반환) | pg-simulator가 비동기(제출 100~500ms→PENDING, 승인 1~5s 비동기). 최종까지 동기 대기 = 안티패턴. resilience는 **제출 호출**을 보호 |

### D4 흐름
```
[client] POST /api/v1/payments {orderId, cardType, cardNo}
   → 주문 조회·권한·금액 확정 (Tx)
   → PG 제출 호출 (Tx 밖, ~100~500ms, @CircuitBreaker/readTimeout 보호)
        · 성공: transactionKey 수신 → Payment PENDING 저장
        · 타임아웃/서킷오픈/실패: Fallback → Order.markUnknown()
   → client 에 "접수됨(PENDING)" 즉시 반환   ← 1~5s 절대 안 기다림
[PG] 1~5s 뒤 비동기 승인 → callback (성공70/한도20/카드10)
   → Payment·Order 를 PAID/FAILED 로 갱신
[안전망] 콜백 유실/UNKNOWN → reconcile (GET 상태조회) 로 확정
```

---

## 열린 결정 포인트 (구현하며 같이 결정)

- **O1. reconcile 범위·실행 위치**: 대상은 명확(PENDING 콜백유실 + UNKNOWN 타임아웃 → `GET /payments/{key}`/`?orderId=`로 확정). **언제·어디서** 미정 — ① 수동 API ② `@Scheduled`(commerce-api) ③ Spring Batch(commerce-batch, 학습 트랙·cross-app 중복 비용).
- **O2. Timeout 값 + CircuitBreaker 임계값**: readTimeout(예 0.8~1s, pg 요청지연 max 500ms 기준), failureRateThreshold / slidingWindow / waitDurationInOpenState / slowCall.
- **O3. 멱등성 / Retry(nice-to-have)**: POST 제출은 **비멱등** → 무지성 재시도 시 중복 거래. 후보 — 주문당 활성 결제 1건 가드 / 멱등키 / Retry는 조회(GET)에만.
- **O4. 클라이언트 최종 결과 전달**: PENDING 반환 후 PAID/FAILED 통지 — 주문/결제 상태 폴링 API(최소안) vs SSE/push(과함).
- **O5. 콜백 수신 세부**: `/payments/callback` 인증 필터 제외 + body 검증 + 콜백·reconcile 중복 도착 멱등 처리.
- **O6. 테스트 전략**: pg-simulator 실제 기동(E2E) vs MockWebServer vs gateway mock bean.
- **O7. 글쓰기 트랙**: Design Doc/Retrospective/Challenge/Benchmark 중 택1 (핵심 후순위).

### 확장 트랙 (해보고 싶은 것 — must-have 이후)
- **O8. Observability 범위 + 알람 신호 (고민)**: 어디까지 갈지 미정.
  - **Tier 1 (최소·과제 충족)**: 기존 Micrometer→Prometheus→Grafana + `resilience4j-micrometer`. CB 상태/실패율 메트릭 + Grafana 알람.
  - **Tier 2 (OTel 확장 — 해보고 싶은 것)**: Micrometer Observation(이미 on) → `micrometer-tracing-bridge-otel` + OTLP exporter → **OTel Collector** → Tempo(trace) + Prometheus(metric) [+ Loki(log)] → Grafana. 결제 1건 **lifecycle 분산추적**(요청→PG제출→콜백→reconcile), **Collector에서 span-derived metric / tail-sampling 으로 "필요한 알람만" 추출**. Tier1→Tier2는 additive(리라이트 아님). ⚠️ 별도 미니프로젝트급 스코프(YAGNI) — 의도적 학습 투자로만.
  - **알람 신호(공통, 어느 Tier든)**: ✅ CircuitBreaker CLOSED→OPEN 전이 ✅ UNKNOWN/PENDING 백로그 임계 초과 ❌ 개별 fallback/실패(40% 실패는 정상, 노이즈). `onStateTransition` 이벤트 컨슈머 → 구조적 WARN, 페이징은 메트릭 기반.
- **O9. 멀티 PG 폴백 안전 정책**: pg-simulator 2개(8082/8092)를 PG-A·PG-B로. ⚠️ **타임아웃 failover = 이중결제 함정.** 규칙 — failover는 **호출이 안 일어난 게 확실할 때만**(CircuitBreaker OPEN=`CallNotPermittedException` / Connection refused). **TimeoutException·전송 후 5xx → failover 금지 → UNKNOWN.** fallbackMethod가 예외 타입 구분 필수.

---

## 생성/수정 파일

**생성** (`commerce-api/.../payment/`)
- `domain/`: `Payment.kt`, `PaymentStatus.kt`, `PaymentErrorCode.kt`, `PaymentRepository.kt`
- `application/`: `PaymentService.kt`(+Command/Info), `PgPaymentGateway.kt`(port)
- `infrastructure/`: `PaymentRepositoryImpl.kt`+`PaymentJpaRepository.kt`, `PgFeignClient.kt`, `PgPaymentGatewayImpl.kt`, DTO
- `interfaces/`: `PaymentController.kt`(+요청/응답/콜백 DTO)
- `config/`: `@EnableFeignClients` (+필요 시 resilience config)

**수정**
- `gradle.properties`, `commerce-api/build.gradle.kts`
- `commerce-api/.../resources/application.yml`
- `account/infrastructure/security/AccountHeaderAuthenticationFilter.kt` (callback 경로 `shouldNotFilter`)

**재사용(무변경)**: `Order.confirmPayment/failPayment/markUnknown`, `OrderService.findById`

---

## 검증 방법

1. 인프라/PG: `docker-compose -f ./docker/infra-compose.yml up`, `./gradlew :apps:pg-simulator:bootRun --args='--spring.profiles.active=local'` (port 8082)
2. commerce-api: `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'`
3. 정상: 주문 생성 → `POST /api/v1/payments` → PENDING → 콜백/조회로 PAID
4. 장애: pg-simulator 중단/지연 → 제출 타임아웃·서킷오픈 → **Fallback→Order UNKNOWN** + **클라이언트엔 PENDING 응답** → reconcile로 확정
5. 테스트: `./gradlew :apps:commerce-api:test`

---

## 범위 밖 (이번 주 제외)
- AI Skill Quest(`analyze-external-integration`)
- 다중 인스턴스 스케줄러 중복 방지(ShedLock 등), 멀티 PG사 추상화
</content>
