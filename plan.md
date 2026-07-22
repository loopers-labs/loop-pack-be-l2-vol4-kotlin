# Round 10 Plan - 주간·월간 상품 랭킹 Batch

## References

- Round 10 Quests: https://www.notion.so/Round-10-Quests-3a36b884cf69803b9829f253b633c7f5
- 기존 일간 랭킹: Redis 실시간 집계 및 조회

## Goal

- 별도 Kafka consumer group이 상품 이벤트를 `product_metric_daily`에 일 단위로 실시간 증분한다.
- Spring Batch가 일간 SOT를 DB에서 기간 단위로 집계해 조회 전용 MV에 바로 저장한다.
- Ranking API가 일간·주간·월간 랭킹을 제공한다.
- 주간·월간 조회는 Redis cache-aside를 사용하고, miss 시 RDB MV의 TOP 100을 적재한다.
- 기존 Daily carry-over 내부 scheduler를 외부 트리거 Spring Batch Job으로 이전한다.

## Fixed Decisions

### Scope

- 업무 테이블은 다음 3개다.
  - `product_metric_daily`
  - `mv_product_rank_weekly`
  - `mv_product_rank_monthly`
- `mv_product_rank_weekly/monthly`는 TOP 100만 저장하지 않고 해당 기간의 전체 상품 점수를 저장한다.
- TOP 100은 `(base_date, ranking_score desc, product_id asc)` 인덱스로 조회한다.
- `product_rank_score_daily`와 Daily RDB fallback은 구현하지 않는다.
- 기존 Daily Redis 실시간 랭킹과 관리자 가중치 변경 기능은 유지한다.
- Batch 완료 후 늦게 도착한 과거 이벤트의 자동 보정은 구현하지 않는다.

### Daily Metric

```text
Catalog/Order Kafka event
    -> commerce-product-metric-daily consumer group
    -> product_metric_daily atomic increment
```

- 이벤트 날짜는 `occurredAt`을 `Asia/Seoul`로 변환해 결정한다.
- 조회 이벤트는 `view_count + 1`이다.
- 좋아요는 `PRODUCT_LIKED + 1`, `PRODUCT_UNLIKED - 1`이며 일별 순증감은 음수를 허용한다.
- 판매 지표는 `PAYMENT_SUCCEEDED` item의 `unitPrice * quantity`를 상품별로 합산한 `sales_amount`다.
- 수량과 금액 계산은 overflow를 검사한다.
- DB 증분은 `SELECT -> UPDATE` 대신 unique key 기반 atomic upsert를 사용한다.
- Daily metric 반영과 event handled 기록은 같은 DB transaction에서 처리한다.

### Group-Aware Idempotency

- 기존 `event_handled`에 `consumer_group`을 추가한다.
- 기존 eventId 단독 unique index를 `(consumer_group, event_id)` unique index로 변경한다.
- 멱등성 repository 계약은 `exists(consumerGroup, eventId)` 형태로 변경한다.
- 적용 group:
  - `loopers-default-consumer`
  - `commerce-coupon-issue`
  - `commerce-product-metric-daily`
- `commerce-ranking`은 기존 Redis processed set을 계속 사용한다.
- 기존 데이터 migration 시 event type을 기준으로 coupon 처리 이력을 `commerce-coupon-issue`, 나머지를
  `loopers-default-consumer`로 backfill한다.

### Score

주간·월간 점수는 기간별 raw metric을 합산한 후 한 번 계산한다.

```text
rankingScore =
    totalViewCount * activeViewWeight
    + totalLikeDelta * activeLikeWeight
    + ln(1 + totalSalesAmount) * activeSalesWeight
```

- 주간·월간에는 Daily carry-over를 포함하지 않는다.
- Batch Job 시작 시 Redis 활성 가중치를 한 번 읽어 Job 전체에서 동일한 snapshot을 사용한다.
- Redis에 활성 가중치가 없으면 설정 기본값 `0.05/0.4/1.0`을 사용한다.
- 동점은 `product_id asc`로 결정한다.

### Base Date and Source Range

- 모든 Job은 외부 Jenkins 또는 GCP Cloud Run Job이 실행한다.
- `baseDate=yyyy-MM-dd` Job parameter는 필수다.
- 서버 현재 시각에서 기준일을 암묵적으로 계산하지 않는다.
- timezone은 `Asia/Seoul`로 고정한다.

#### Weekly

```text
Trigger: 매주 월요일 01:00
baseDate: 실행되는 월요일
source: [baseDate - 7일, baseDate)
```

- 직전 월요일부터 일요일까지 집계한다.
- `base_date`에는 랭킹을 노출하는 현재 주 월요일을 저장한다.
- Weekly Job은 `baseDate`가 월요일이 아니면 실패한다.
- 조회 날짜는 `previousOrSame(MONDAY)`로 정규화한다.

#### Monthly

```text
Trigger: 매월 1일 02:00
baseDate: 실행되는 현재 월 1일
source: [baseDate - 1개월, baseDate)
```

- 직전 달 전체를 집계한다.
- `base_date`에는 랭킹을 노출하는 현재 월 1일을 저장한다.
- Monthly Job은 `baseDate.dayOfMonth != 1`이면 실패한다.
- 조회 날짜는 해당 월의 1일로 정규화한다.

### Daily Carry-Over Batch

- 기존 `RankingCarryOverScheduler`와 API 내부 `@Scheduled` 실행을 제거한다.
- 기존 Redis key, Lua 원자성, TOP 100, 10% carry factor, 분산 락 및 TTL 정책은 유지한다.
- carry-over application service와 Redis adapter를 `commerce-batch` 책임으로 이전한다.
- 외부 scheduler가 매일 23:50 KST에 `dailyRankingCarryOverJob`을 실행한다.
- Job parameter의 `baseDate`는 carry가 반영될 대상 날짜다.

```text
Trigger: 2026-08-03 23:50
baseDate: 2026-08-04
sourceDate: baseDate - 1일
targetDate: baseDate
```

- source의 `ranking:all` TOP 100 점수에 0.1을 곱해 target carry를 만든다.
- target의 carry/view/like/sales를 활성 가중치로 합산해 `ranking:all`을 생성한다.
- Daily carry-over 결과를 RDB에 저장하지 않는다.
- Daily API는 기존 Redis-only 조회를 유지하고 RDB fallback을 제공하지 않는다.

### API and Cache

```http
GET /api/v1/rankings?date=20260805&period=DAILY&page=0&size=20
GET /api/v1/rankings?date=20260805&period=WEEKLY&page=0&size=20
GET /api/v1/rankings?date=20260805&period=MONTHLY&page=0&size=20
```

- `period`는 `DAILY`, `WEEKLY`, `MONTHLY`를 지원한다.
- `period` 생략 시 `DAILY`로 처리해 기존 API 호환성을 유지한다.
- Daily는 기존 `ranking:all:{yyyyMMdd}` Redis 조회를 유지한다.
- Weekly/Monthly는 요청 날짜를 baseDate로 정규화한 뒤 발행 완료된 baseDate만 조회한다.
- 요청 baseDate가 아직 발행되지 않았으면 직전 발행 baseDate로 fallback한다.
- Weekly/Monthly는 Redis miss 시 발행된 RDB MV에서 해당 `base_date` TOP 100 전체를 조회한다.
- RDB에서 조회한 TOP 100 전체를 generation별 Redis Sorted Set에 적재한 뒤 요청 page를 반환한다.
- Weekly cache TTL은 8일, Monthly cache TTL은 32일이다.
- Batch 성공 후 Redis cache를 삭제하지 않고 `product_rank_publication`에 새 generation을 발행한다.
- Redis key:
  - `ranking:weekly:{yyyyMMdd}:{generationId}`
  - `ranking:monthly:{yyyyMMdd}:{generationId}`
- cache fill 동시 요청은 짧은 SETNX lock으로 제어한다.
- 발행되지 않은 baseDate의 빈 결과는 캐시하지 않는다.
- 주간·월간 API의 `totalElements`는 최대 100이다.

## Table Design

모든 엔티티는 기존 `BaseEntity`의 `id`, `created_at`, `updated_at`, `deleted_at`을 사용한다.
`base_date`와 `product_id`는 기간별 MV 업무 unique key다.

### product_metric_daily

```sql
CREATE TABLE product_metric_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_metric_daily_date_product (metric_date, product_id),
    KEY idx_product_metric_daily_product_date (product_id, metric_date)
);
```

### mv_product_rank_weekly

```sql
CREATE TABLE mv_product_rank_weekly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    base_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    ranking_score DOUBLE NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_product_rank_weekly_base_product (base_date, product_id),
    KEY idx_mv_product_rank_weekly_top (
        base_date,
        ranking_score DESC,
        product_id ASC
    )
);
```

### mv_product_rank_monthly

```sql
CREATE TABLE mv_product_rank_monthly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    base_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    ranking_score DOUBLE NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_product_rank_monthly_base_product (base_date, product_id),
    KEY idx_mv_product_rank_monthly_top (
        base_date,
        ranking_score DESC,
        product_id ASC
    )
);
```

### product_rank_publication

```sql
CREATE TABLE product_rank_publication (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period VARCHAR(20) NOT NULL,
    base_date DATE NOT NULL,
    generation_id VARCHAR(64) NOT NULL,
    published_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_rank_publication_period_base (period, base_date),
    KEY idx_product_rank_publication_lookup (period, base_date)
);
```

## Batch Processing Design

### Metric Aggregation Reader

- `JdbcCursorItemReader`가 DB에서 상품 단위로 미리 합산된 row를 streaming한다.
- 애플리케이션에서 일별 row를 모두 읽어 전역 Map으로 합산하지 않는다.
- Reader SQL:

```sql
SELECT
    product_id,
    SUM(view_count) AS view_count,
    SUM(like_count) AS like_count,
    SUM(sales_amount) AS sales_amount
FROM product_metric_daily
WHERE metric_date >= :sourceStart
  AND metric_date < :sourceEndExclusive
GROUP BY product_id
ORDER BY product_id
```

- Reader item 하나는 기간 내 한 상품의 합산 metric이다.
- Reader item을 중간 weekly/monthly SOT에 저장하지 않고 score processor로 바로 전달한다.
- Reader fetch size와 chunk size는 configuration으로 분리한다.
- 초기값은 fetch size 1,000, chunk size 500으로 둔다.

### Score Materialization Step

- Job 시작 시 확정한 가중치 snapshot을 Step ExecutionContext에 저장한다.
- Reader가 source range의 daily metric aggregate를 `product_id` 순서로 streaming한다.
- Processor가 `ln(1 + totalSalesAmount)`와 가중합을 계산한다.
- Writer가 전체 상품 score를 weekly/monthly MV에 chunk upsert한다.
- 재실행 전에 해당 `base_date`의 기존 MV row를 삭제해 원천에서 사라진 상품이 남지 않게 한다.
- Step 성공 후 RDB TOP 100 query의 정렬과 tie-breaker를 검증한다.
- Job 전체 성공 후에만 해당 period/baseDate의 publication generation을 발행한다.

### Large Dataset Policy

- 전체 상품을 JVM collection에 적재하지 않는다.
- TOP 100을 Processor의 전역 heap으로 관리하지 않는다.
- 기간 집계는 DB `GROUP BY product_id`, score 계산은 chunk processor가 담당한다.
- TOP 100은 MV covering index가 담당한다.
- 기본 구현은 단일 cursor reader로 시작한다.
- 실행 시간이 운영 기준을 넘으면 `product_id` range partitioning을 추가할 수 있게 Reader query 경계를 분리한다.
- partitioning 자체는 이번 구현 범위에서 제외한다.

## Working Rule

- 사용자가 `go`라고 하면 아래에서 완료되지 않은 가장 앞의 Feature Unit 하나를 작업한다.
- 한 번에 하나의 실패 테스트를 추가하고 Red -> Green -> Refactor를 따른다.
- 구조 변경과 동작 변경은 별도 커밋으로 분리한다.
- Feature Unit 완료 후 관련 모듈 테스트와 ktlint를 실행한다.
- Docker가 필요한 통합 테스트를 실행하지 못하면 testClasses 결과와 남은 리스크를 기록한다.
- 완료된 Feature Unit의 checkbox만 갱신한다.

## Feature Units

### [x] 1. Group-Aware Event Idempotency

#### 작업

- `event_handled.consumer_group` 컬럼과 `(consumer_group, event_id)` unique index를 추가한다.
- domain/entity/repository 계약에 consumer group을 반영한다.
- Catalog, Order, Coupon processor가 자신의 실제 group ID로 멱등성을 확인하고 기록하게 한다.
- 기존 데이터 backfill migration SQL을 작성한다.

#### 필수 테스트

- 동일 eventId를 서로 다른 consumer group에서 각각 한 번씩 처리할 수 있다.
- 같은 group의 동일 eventId는 한 번만 처리된다.
- 기존 Catalog/Order/Coupon consumer 테스트가 group-aware 계약으로 통과한다.

#### 완료 기준

- consumer group 간 멱등성 상태가 충돌하지 않는다.
- 기존 이벤트 처리 의미가 변경되지 않는다.

### [x] 2. Daily Product Metric SOT

#### 작업

- `product_metric_daily` domain/entity/repository를 Streamer에 추가한다.
- Catalog/Order topic에 `commerce-product-metric-daily` batch consumer group을 추가한다.
- Catalog view/like delta와 PAYMENT_SUCCEEDED sales amount를 atomic upsert한다.
- 이벤트 handled 기록과 metric 증분을 같은 transaction으로 묶는다.
- occurredAt KST 날짜, validation, overflow, ack/DLT 정책을 적용한다.

#### 필수 테스트

- View/Like/Unlike가 날짜·상품별로 정확히 증분된다.
- 결제 item의 `unitPrice * quantity`가 상품별 sales amount로 합산된다.
- 동일 eventId 재전달은 metric을 중복 증가시키지 않는다.
- 다른 consumer group에서 처리한 동일 eventId는 Daily metric 처리에 영향을 주지 않는다.
- 자정 경계 이벤트가 KST 날짜로 분리된다.

#### 완료 기준

- `product_metric_daily`가 주간·월간 Batch의 재현 가능한 일별 SOT가 된다.
- 상품 수와 무관하게 이벤트당 영향 상품만 갱신한다.

### [x] 3. Period Metric Schema and Job Parameters

#### 작업

- weekly/monthly MV entity/repository를 Batch 모듈에 추가한다.
- 공통 `baseDate`, source range 계산 정책을 구현한다.
- Weekly 월요일 validation과 Monthly 1일 validation을 추가한다.
- chunk/fetch size와 cache TTL configuration properties를 추가한다.

#### 필수 테스트

- Weekly baseDate에서 직전 월요일~일요일 범위를 계산한다.
- Monthly baseDate에서 직전 달의 반개구간을 계산한다.
- 잘못된 baseDate 또는 누락된 Job parameter는 Job을 실패시킨다.

#### 완료 기준

- 모든 Job이 실행 시각과 무관하게 명시적 baseDate로 재실행 가능하다.

### [x] 4. Weekly Product Ranking Job

#### 작업

- `weeklyProductRankingJob`을 daily metric aggregation reader와 score materialization Step으로 구성한다.
- Daily metric을 DB에서 product별 GROUP BY하여 cursor로 읽는다.
- Daily metric aggregation result를 바로 score processor로 전달해 Weekly MV를 chunk upsert한다.
- Redis 활성 가중치 snapshot으로 전체 weekly MV score를 계산한다.
- Job 성공 후 weekly publication generation을 발행한다.

#### 필수 테스트

- 여러 날짜와 chunk에 걸친 동일 상품 metric이 하나의 weekly row로 합산된다.
- 판매금액 로그는 일별 점수 합이 아니라 기간 총 판매금액에 한 번 적용된다.
- carryScore가 weekly score에 포함되지 않는다.
- 동점 score는 productId 오름차순으로 결정된다.
- 동일 baseDate 재실행 결과가 중복 없이 동일하다.

#### 완료 기준

- 상품 전체를 JVM에 적재하지 않고 전체 weekly score MV를 생성한다.
- 인덱스로 해당 baseDate TOP 100을 조회할 수 있다.

### [x] 5. Monthly Product Ranking Job

#### 작업

- Weekly Job의 공통 Reader/Processor/Writer 구성요소를 재사용해 `monthlyProductRankingJob`을 구현한다.
- 직전 월 Daily metric aggregation result를 바로 score processor로 전달한다.
- 동일 가중치 정책으로 전체 monthly MV score를 계산한다.
- Job 성공 후 monthly publication generation을 발행한다.

#### 필수 테스트

- 월 길이와 연도 경계에 관계없이 직전 월 전체만 집계한다.
- 여러 chunk의 상품 metric이 정확히 합산된다.
- 동일 baseDate 재실행이 멱등하다.
- Monthly TOP 100 query가 score desc/productId asc 순서를 따른다.

#### 완료 기준

- Weekly와 Monthly가 정책만 다르고 처리 파이프라인은 중복 없이 공유한다.

### [x] 6. Daily Carry-Over Batch Migration

#### 작업

- API의 `RankingCarryOverScheduler`를 제거한다.
- carry-over service, Redis port/adapter와 Lua resource를 Batch 모듈 경계로 이전한다.
- `dailyRankingCarryOverJob`을 외부 트리거와 필수 baseDate parameter로 구성한다.
- 기존 TOP 100, factor 0.1, active weights, 분산 락, 원자적 Lua, 고정 TTL을 보존한다.
- Daily RDB score 저장이나 fallback은 추가하지 않는다.

#### 필수 테스트

- baseDate의 전날 TOP 100만 target carry로 이동한다.
- carry score는 source final score의 10%다.
- 동일 baseDate 중복 실행은 최종 결과가 동일하다.
- 분산 락을 얻지 못한 실행은 target을 변경하지 않는다.
- API application context에 carry scheduler가 더 이상 존재하지 않는다.

#### 완료 기준

- Daily carry-over가 API 프로세스 수와 무관하게 외부 Batch 실행으로 한 번만 수행된다.
- 기존 Daily Ranking API 동작은 변경되지 않는다.

### [ ] 7. Period Ranking API and Redis Cache-Aside

#### 작업

- Ranking period enum과 기본값 DAILY를 API/application DTO에 추가한다.
- Weekly 날짜는 월요일, Monthly 날짜는 월 1일 baseDate로 변환한다.
- Daily는 기존 Redis repository로 routing한다.
- Weekly/Monthly는 period Redis repository와 RDB MV fallback repository로 routing한다.
- 미발행 baseDate는 직전 발행 baseDate로 fallback한다.
- miss 시 TOP 100 전체 적재, generation별 cache key, cache fill lock, TTL을 구현한다.
- 페이지 응답을 cache된 TOP 100 범위로 제한한다.
- API spec과 `.http` 예시를 갱신한다.

#### 필수 테스트

- period 생략이 기존 Daily 응답과 동일하다.
- Weekly의 월 경계 날짜들이 같은 월요일 baseDate를 조회한다.
- Monthly의 모든 날짜가 같은 월 1일 baseDate를 조회한다.
- Weekly/Monthly cache hit에서는 RDB를 호출하지 않는다.
- cache miss에서는 RDB TOP 100을 한 번 적재하고 이후 Redis에서 조회한다.
- 요청 baseDate가 아직 미발행이면 빈 결과를 캐시하지 않고 직전 발행 baseDate를 조회한다.
- Weekly 8일, Monthly 32일 TTL을 검증한다.
- 빈 MV와 Redis 장애를 구분한다.

#### 완료 기준

- 하나의 API가 기존 호환성을 유지하며 세 기간 랭킹을 제공한다.
- 주간·월간 cache miss가 전체 상품 정렬이나 N+1 상품 조회를 유발하지 않는다.

### [ ] 8. Query Plan and End-to-End Verification

#### 작업

- Daily range GROUP BY query와 Weekly/Monthly TOP 100 query에 `EXPLAIN`을 수행한다.
- large fixture에서 chunk 처리 시 JVM 전체 적재가 없는지 확인한다.
- Batch -> MV -> cache miss -> Redis hit API 흐름을 통합 검증한다.
- 외부 실행 명령과 baseDate 예시를 문서화한다.

```shell
java -jar commerce-batch.jar \
  --spring.batch.job.name=weeklyProductRankingJob \
  baseDate=2026-08-03

java -jar commerce-batch.jar \
  --spring.batch.job.name=monthlyProductRankingJob \
  baseDate=2026-08-01

java -jar commerce-batch.jar \
  --spring.batch.job.name=dailyRankingCarryOverJob \
  baseDate=2026-08-04
```

#### 필수 테스트

- Weekly/Monthly Job E2E에서 Batch metadata와 결과 row를 검증한다.
- Ranking API E2E에서 cache miss와 hit 결과가 동일하다.
- 기존 Daily Ranking과 상품 상세 rank 회귀 테스트가 통과한다.

#### 완료 기준

- Round 10 Notion checklist의 Spring Batch, chunk processing, MV, 기간별 Ranking API를 모두 충족한다.
- 관련 모듈 test, ktlint와 핵심 E2E가 통과한다.
