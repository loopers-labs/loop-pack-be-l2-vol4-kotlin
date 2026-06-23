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
