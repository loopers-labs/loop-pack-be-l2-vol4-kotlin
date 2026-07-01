# K6 부하테스트 — 주문 생성 → 결제 → (PG 자동 콜백)

`주문 생성 → 결제 요청` 흐름을 부하 측정한다. 결제 콜백은 **PG 시뮬레이터(8082)가 비동기로 자동 호출**하는 구조라 K6가 직접 호출하지 않는다. 콜백 완료(주문 `PAYMENT_COMPLETED` 전이)는 옵션 폴링으로 검증한다.

## 구성 파일

| 파일 | 역할 |
|---|---|
| `seed.sql` | 유저/브랜드/상품/재고 사전 주입. 비번은 SHA-256(앱과 동일)이라 SQL 로 심어도 로그인 통과 |
| `order-payment-flow.js` | K6 시나리오 (주문→결제, 옵션 콜백 검증) |

## 사전 준비

### 1) 인프라 + 두 앱 실행
```bash
# MySQL/Redis/Kafka
docker-compose -f ./docker/infra-compose.yml up -d

# commerce-api (8080)
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'

# PG 시뮬레이터 (8082) — 콜백을 발사하는 주체. 반드시 같이 떠 있어야 함
./gradlew :apps:pg-simulator:bootRun --args='--spring.profiles.active=local'
```
> 두 앱은 ddl-auto=update 라 첫 기동 시 스키마가 생성된다. **앱을 먼저 띄워 테이블을 만든 뒤** 시드를 넣는다.

### 2) 시드 데이터 주입
```bash
mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers < k6/seed.sql
```
출력되는 `brand_id` 값을 기억한다(다음 단계 `BRAND_ID`). 규모는 `seed.sql` 상단 `@USER_COUNT`/`@PRODUCT_COUNT`/`@STOCK_PER_ITEM` 로 조정.

### 3) K6 설치
```bash
brew install k6      # macOS
```

## 실행

```bash
# 기본 (주문→결제만 측정, 가장 현실적인 처리량)
k6 run -e BRAND_ID=<시드출력값> k6/order-payment-flow.js

# 콜백 완료까지 검증 (주문 상태 폴링 — 처리량은 왜곡되니 정합성 확인용)
k6 run -e BRAND_ID=<시드출력값> -e VERIFY_CALLBACK=true k6/order-payment-flow.js

# 유저 수를 시드와 다르게 잡았다면 USER_COUNT 도 맞춘다
k6 run -e BRAND_ID=12 -e USER_COUNT=200 -e BASE_URL=http://localhost:8080 k6/order-payment-flow.js
```

## Grafana 로 보기 (k6 + 서킷 브레이커 한 화면)

k6 결과를 Grafana 에서 보고, **서킷 브레이커 적용 전후를 같은 타임라인에 겹쳐** 비교한다.
앱의 `resilience4j_circuitbreaker_*` 메트릭은 이미 `:8081/actuator/prometheus` 로 노출되어 Prometheus 가 긁고 있고,
k6 메트릭만 Prometheus 로 remote write 하면 둘이 한 대시보드에서 만난다.

### 1) 모니터링 스택 실행
```bash
docker-compose -f ./docker/monitoring-compose.yml up -d
```
- Prometheus 는 `--web.enable-remote-write-receiver` 로 k6 수신을 켜둔 상태.
- Grafana(`http://localhost:3000`, admin/admin) 에 **"k6 × Circuit Breaker (PG 결제)"** 대시보드가 자동 프로비저닝된다(폴더: Loopers).

### 2) k6 를 remote write 출력으로 실행
```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg,max" \
k6 run -o experimental-prometheus-rw \
  -e BRAND_ID=<시드출력값> k6/order-payment-flow.js
```
> `K6_PROMETHEUS_RW_TREND_STATS` 가 `biz_payment_duration_p95` 같은 시리즈를 만든다(대시보드 패널이 이 이름을 참조).

### 3) 적용 전후 비교 (연속 2회, 한 타임라인)

서킷 브레이커는 **PG 동기 호출이 실패/지연될 때** 의미가 있다. 따라서 비교하려면 PG 장애를 유발한 채로
"CB 무력화 런 → CB 정상 런" 을 **연속으로** 돌리고, 대시보드에서 왼쪽(전) / 오른쪽(후) 을 본다.

**PG 장애 유발(택1)**
- PG 시뮬레이터를 내린다: `:apps:pg-simulator` 종료 → 동기 호출이 타임아웃/거부.
- 또는 죽은 포트로 돌린다: 앱에 `-Dpg-simulator.url=http://localhost:9999`.

**① CB 미적용처럼 (서킷이 절대 안 열리게) — "전"**
최소 호출수를 비현실적으로 키워 서킷이 항상 closed 로 머물게 한다. 앱 기동 시:
```bash
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local \
  --resilience4j.circuitbreaker.instances.pgPayment.minimum-number-of-calls=100000000'
```
→ 이 상태로 위 2) 의 k6 1회 실행. 지연이 치솟고 에러/재시도가 그대로 누적된다.

**② CB 적용 — "후"**
앱을 기본 설정으로 재기동(오버라이드 제거) → 같은 PG 장애 상태에서 k6 1회 더 실행.
서킷이 open 되며 **차단된 호출(not permitted)** 이 발생하고, 결제 p95 가 fast-fail 로 상한이 잡힌다.

대시보드에서 두 런이 시간축에 나란히 찍히므로, **서킷 상태 타임라인의 `open` 레인**과
위쪽 **지연 p95 / 에러율** 패널의 시점을 맞춰 보면 효과가 한눈에 드러난다.
(CB open 구간은 빨간 annotation 으로도 표시된다.)

> 참고: 범용 k6 지표만 보고 싶으면 Grafana 공식 대시보드 **ID 19665**(k6 Prometheus) 를 import 해도 된다.
> 단, 서킷 상태 오버레이는 본 저장소 대시보드에만 있다.

## 부하 프로파일

`ramping-vus`: 20 → 50 → 100 VU 로 약 3분. `order-payment-flow.js` 의 `options.scenarios.order_payment.stages` 에서 조정한다.

## 결과 해석 (커스텀 메트릭)

| 메트릭 | 의미 |
|---|---|
| `biz_order_duration` | 주문 생성 응답시간 (p95 < 500ms 임계) |
| `biz_payment_duration` | 결제 요청 응답시간. **PG 동기 호출(100~500ms) 포함** (p95 < 800ms) |
| `biz_order_success` | 주문 생성 성공률 |
| `biz_payment_accepted` | 결제 PG 접수(PENDING) 성공률 |
| `biz_callback_completed` | (검증 모드) 콜백까지 완료된 비율. PG가 ~40% 실패하므로 **약 60% 부근이 정상** |
| `biz_flow_errors` | 흐름 중 실패 건수 |

## 주의 / 흔한 함정

- **PG 시뮬레이터가 떠 있어야 한다.** 안 떠 있으면 결제 단계에서 connection refused 로 전부 실패한다.
- **콜백은 비동기.** 결제 응답은 `PENDING`으로 즉시 반환되고 완료 전이는 뒤따른다. 처리량(주문/결제) 측정과 콜백 정합성 검증은 분리해서 본다(`VERIFY_CALLBACK`).
- **PG 실패율 40%는 의도된 설정.** 결제 *접수*는 성공해도 콜백 결과는 실패할 수 있다. `biz_payment_accepted`(접수)와 `biz_callback_completed`(최종 완료)를 구분할 것.
- **재고 락 경합.** 재고 차감은 상품 행 단위 비관적 락이다. 상품 수가 적으면 같은 행 경합이 병목으로 잡힌다. 순수 처리량을 보려면 `@PRODUCT_COUNT`를 늘리고, 락 경합을 일부러 보려면 상품 수를 줄인다.
- **재실행.** `seed.sql`은 `loadtest%` 데이터를 지우고 다시 넣으므로 반복 실행해도 안전. 단 매번 새 `brand_id`가 나올 수 있으니 출력값을 다시 쓴다.
```
