# Round 9 — Redis ZSET 실시간 상품 랭킹 설계

- 작성일: 2026-07-17
- 브랜치: `volume-9` (volume-8 PR #122 병합 커밋에서 시작, base=yeonjoo7로 PR 예정)
- 범위: 랭킹 컨슈머(일간+시간 키 적재) + 랭킹 Page API(상품정보 aggregation) + 상품 상세 rank. 통합 설계 1개.
- 변경 이력: 2026-07-17 — 구현 중 보강: date 검증 STRICT resolver(달력상 불가능한 날짜 거부), PAYMENT_SUCCEEDED 음수 수량·단가 아이템 스킵(NaN 점수의 파이프라인 배치 오염 방지). 리뷰 지적 반영.

---

## 1. 목표

R7 Kafka 파이프라인(catalog-events, order-events)이 나르는 유저 행동 이벤트(조회·좋아요·주문)를 기반으로 Redis ZSET 실시간 랭킹을 구축한다.

- **실시간 집계**: collector(commerce-streamer)가 이벤트를 소비하며 ZINCRBY로 점수 누적 — DB `GROUP BY + ORDER BY` 없이 정렬 내장.
- **시간의 양자화**: 일간(`yyyyMMdd`)·시간(`yyyyMMddHH`) 윈도우 키 분리 + TTL — 누적 랭킹의 롱테일 독식 방지.
- **가중치 합산**: 조회/좋아요/주문 신호를 가중치로 스케일 맞춰 단일 스코어에 합산.
- **조회 API**: Top-N 페이지(상품정보 aggregation) + 상품 상세의 개별 순위(미진입 시 null).

**Nice-to-have 채택**: 시간 단위(1시간) 랭킹 — 적재 + `period` 파라미터로 API 노출.
**제외**: 콜드 스타트 carry-over 스케줄러, 실시간 weight 조절(프로퍼티 외부화까지만). Kafka 배치 리스너는 R7 기존 패턴이라 자동 충족.

## 2. 현재 코드베이스 상태 (탐색 결과)

**재사용:**
- 컨슈머: `MetricsConsumer`가 `catalog-events`/`order-events`를 `KafkaConfig.BATCH_LISTENER`(배치+수동 ack)로 소비. per-record `runCatching` 스킵(포이즌 메시지 격리). `MetricEventMapper`(JSON tree 파싱) → `ProductMetricsService.applyOnce`(event_handled 테이블 멱등 + product_metrics upsert, 한 DB 트랜잭션).
- 이벤트 타입: `PRODUCT_VIEWED{productId}`, `LIKE_ADDED/LIKE_REMOVED{productId}`, `PAYMENT_SUCCEEDED{orderId, userId, items[{productId, quantity}]}` — **price 없음**. `OutboxMessageFactory`가 payload 생성(outbox → Kafka).
- Redis: streamer에 `modules:redis` 의존성 이미 존재. `RedisConfig.REDIS_TEMPLATE_MASTER` qualifier, `StringRedisSerializer`, `runCatching` degradation 패턴(R8).
- 상품 조회: `GetProductsUsecase`/`GetProductDetailUsecase` + `ProductInfo`(brand·price·likeCount 포함). `ProductV1Controller`(0-based page).
- 테스트: Redis/Kafka Testcontainers, `RedisCleanUp.truncateAll()`, 3A 패턴.

**Greenfield(신규):** 랭킹 도메인(점수 정책·키 계산), RankingConsumer(별도 그룹), Redis ZSET 리포지토리(쓰기/읽기), 랭킹 API, 상품 상세 rank 필드.

## 3. 확정된 결정

| # | 결정 | 채택 | 근거 |
|---|---|---|---|
| D1 | 범위 | **Must-have + 시간 단위 랭킹** | 콜드 스타트·실시간 weight는 제외. hourly는 키 전략을 period로 일반화해 흡수 |
| D2 | 주문 price 소싱 | **`PaymentSucceededEvent.Item`에 `unitPrice` 추가** | 필드 추가는 하위호환(기존 metrics 컨슈머 무영향). 주문 시점 가격 정확, 컨슈머 DB 조회 불필요 |
| D3 | 주문 점수 정규화 | **0.6 × log10(1 + unitPrice×quantity)** | 원금액 그대로면 주문이 조회(0.1)·좋아요(0.2)를 무의미화. log 스케일로 세 신호 모두 순위에 기여하면서 "주문 1건 > 좋아요 3건" 충족(3만원 주문≈2.69 > 좋아요 3건=0.6) |
| D4 | 컨슈머 배치 | **별도 `RankingConsumer`(별도 consumer group)** | Kafka 팬아웃으로 metrics와 오프셋·장애·재소비 완전 독립. 랭킹 Redis 장애가 product_metrics 적재에 무영향 |
| D5 | 멱등성 | **Redis `SET NX`(dedup 키) 통과 시에만 ZINCRBY** | 배치 ack 특성상 재소비 시 중복 가산 방지. R7 event_handled 철학 유지하되 저장소는 Redis(DB 왕복 없음). SETNX→ZINCRBY 사이 크래시의 미세 유실 윈도우는 근사 집계 특성상 허용(문서화) |
| D6 | API 형태 | **`GET /api/v1/rankings?period=DAILY\|HOURLY&date=…&page=1&size=20`** | period 기본 DAILY로 Must-have 스펙과 하위호환. page는 **1-based**(요구사항 예시 `page=1` 우선, 기존 product API 0-based와 다름을 명시) |
| D7 | 키 버전 | **키에 `v1` 포함** (`ranking:all:v1:{yyyyMMdd}`) | R8 컨벤션(`…:token:v1:{userId}`)과 동일 — 점수식·직렬화 변경 시 키 마이그레이션 여지 확보 |

미채택(문서화만): 컨슈머에서 product 테이블 price 조회(현재가≠주문가, DB 결합), 원금액 그대로 합산(신호 불균형), Lua 원자화(근사 집계 대비 과한 복잡도), event_handled 테이블 공유(다른 그룹이라 선점 충돌).

## 4. 아키텍처 / 흐름

```
[commerce-api]
  ProductViewedEvent / LikeCreated·Deleted / PaymentSucceeded(+unitPrice 추가)
    → Outbox → Kafka (catalog-events, order-events)

[commerce-streamer]
  [metrics group]  MetricsConsumer  → event_handled + product_metrics (R7 그대로, 불변)
  [ranking group]  RankingConsumer  → SETNX dedup → ZINCRBY daily+hourly + EXPIREAT  ← 신규

[commerce-api]
  GET /api/v1/rankings?date=…&period=…   → ZREVRANGE WITHSCORES + 상품정보 batch 조회(순서 유지)
  GET /api/v1/products/{id}              → 기존 응답 + rank (오늘 일간 키 ZREVRANK, 미진입 시 null)
```

## 5. Redis 키 / 점수 설계

| 키 | 타입 | TTL | 용도 · 연산 |
|---|---|---|---|
| `ranking:all:v1:{yyyyMMdd}` | ZSET | **EXPIREAT** 윈도우 시작+2일 | 일간 랭킹. `ZINCRBY`(적재), `ZREVRANGE WITHSCORES`(페이지), `ZREVRANK`(개별 순위), `ZCARD`(전체 수) |
| `ranking:hourly:v1:{yyyyMMddHH}` | ZSET | **EXPIREAT** 윈도우 시작+2시간 | 시간 랭킹. 연산 동일 |
| `ranking:handled:v1:{eventId}` | String | `SET NX EX` 2일 | 컨슈머 멱등 dedup. 이벤트당 1개(daily·hourly 갱신을 함께 게이트) |

- **EXPIREAT(절대시각)**: rolling EXPIRE와 달리 쓰기마다 TTL이 밀리지 않고 멱등. 윈도우 시작+2×윈도우 크기 → 어제 랭킹은 오늘 하루 종일 조회 가능(체크리스트 "일자 변경 후 이전 날짜 조회").
- **키 계산**: `Clock` 주입 `RankingKeyResolver` — `dailyKey(date)`, `hourlyKey(dateTime)`, 윈도우 만료시각 계산. 단위 테스트로 자정·정시 경계 검증(체크리스트 "날짜별로 적재할 키를 계산하는 기능").
- **점수식** (가중치 프로퍼티 외부화: `ranking.weight.view/like/order`):

```
PRODUCT_VIEWED     +0.1
LIKE_ADDED         +0.2        LIKE_REMOVED  -0.2  (ZSET 음수 score 허용 — 순위만 하락)
PAYMENT_SUCCEEDED  +0.6 × log10(1 + unitPrice×quantity)   (아이템별 합산)
```

## 6. 컴포넌트 (레이어)

**commerce-api (발행 측 변경, 최소)**
- `PaymentSucceededEvent.Item`에 `unitPrice: BigDecimal` 추가 → 발행 지점에서 주문 아이템 가격 전달 → `OutboxMessageFactory.order()` payload에 `unitPrice` 포함.

**commerce-streamer (쓰기)**
- `domain/ranking`: `RankingScorePolicy` — 가중치(프로퍼티) × 이벤트별 점수 계산(순수, log10 포함). `RankingScoreDelta(productId, score)`. `RankingRepository` port — 배치 단위 프리미티브 `applyAll(entries: List<RankingEntry(eventId, deltas)>, window…)`(멱등 게이트 + 가산; 배치 시그니처라 구현이 2-pass 파이프라인 가능).
- `application/ranking`: `RankingEventMapper` — JSON → 이벤트 타입·payload 추출(metrics 매퍼와 분리해 결합 차단, unitPrice 파싱은 여기만). `RankingService` — mapper→policy→repository 오케스트레이션(배치 단위).
- `infrastructure/ranking`: `RankingRedisRepository` — `@Qualifier(REDIS_TEMPLATE_MASTER)`. 2-pass 파이프라인: ① 배치 전체 `SET NX` 일괄 → ② 통과분만 `ZINCRBY`(daily+hourly)+`EXPIREAT` 일괄. `DataAccessException`만 `runCatching` degradation(랭킹 스킵+로그, 컨슈머 생존).
- `interfaces/consumer`: `RankingConsumer` — 같은 토픽(`catalog-events`,`order-events`), **`groupId` 명시로 별도 그룹**, 기존 `BATCH_LISTENER`, per-record 스킵 패턴 동일.

**commerce-api (읽기)**
- `domain/ranking`: `RankingQueryRepository` port — `page(period, date, offset, size): List<RankedProduct(productId, score)>`, `rank(productId, date): Long?`, `total(period, date): Long`.
- `application/ranking`: `GetRankingsUsecase` — ZREVRANGE 페이지 → productIds batch 조회 → **ZSET 순서 유지** 조립 → `RankingItemInfo(rank, score, 상품 요약)`. 삭제된 상품은 스킵(해당 페이지 항목 수가 size보다 작아질 수 있음 — 문서화). 빈 키(콜드 스타트·미래 날짜)는 빈 목록 정상 응답.
- `application/product`: `GetProductDetailUsecase` 확장 — 오늘 일간 키 `ZREVRANK`+1 → `rank: Long?`(미진입·Redis 장애 시 null degradation).
- `interfaces/api/ranking`: `RankingV1Controller`, `RankingV1Dto` — `date` 포맷은 DAILY=`yyyyMMdd`, HOURLY=`yyyyMMddHH`. 생략 시 현재(DAILY=오늘, HOURLY=현재 시각). 잘못된 포맷 → `CoreException(BAD_REQUEST)`.

## 7. 장애 / 에러 처리

- **컨슈머**: Redis `DataAccessException` → 해당 배치 랭킹 반영 스킵 + `log.error`(경보). metrics 그룹 독립이라 product_metrics 무영향. 그 외 예외는 per-record 스킵(R7 포이즌 메시지 패턴). dedup 키가 이미 박힌 이벤트는 재시도해도 스킵되므로, "SETNX 성공 후 ZINCRBY 전 크래시" 유실 윈도우는 근사 집계 특성상 수용(§3 D5).
- **API**: 랭킹 Page 조회의 Redis 장애는 에러 응답(`ApiControllerAdvice`) — 랭킹은 해당 API의 핵심 데이터. 상품 상세의 rank는 **null degradation** — 상품 정보 제공이 우선.
- **가중치 변경 시 주의(문서화)**: 프로퍼티 변경 후 재배포하면 같은 윈도우 안에서 신·구 가중치 점수가 섞임 — 윈도우 전환(자정) 배포 권장.

## 8. 테스트 전략

TDD(Red→Green→Refactor), Redis/Kafka Testcontainers, 3A.

- **단위**: `RankingScorePolicy` — 이벤트별 점수, LIKE_REMOVED 감산, **주문 1건 > 좋아요 3건**(체크리스트), log10 경계(0원 방어). `RankingKeyResolver` — 일간/시간 키 포맷, 자정·정시 경계, EXPIREAT 시각.
- **통합(streamer)**: 이벤트 배치 소비 → ZSET 점수 반영(daily+hourly), 같은 eventId 재소비 → 중복 가산 없음(SETNX), 알 수 없는 타입 스킵, TTL 설정 확인.
- **통합(api)**: 랭킹 페이지 — 점수순 정렬·상품정보 aggregation·1-based 페이지네이션, 이전 날짜 키 조회, 빈 키 빈 목록. 상품 상세 — rank 반환/미진입 null.
- **E2E**: 이벤트 발행 → outbox → Kafka → 컨슈머 → ZSET → API 조회 전체 흐름(체크리스트 검증 3종 커버).

## 9. 구현 순서 (Step 1 → 4)

- **Step 1 — 발행**: `PaymentSucceededEvent.Item.unitPrice` + `OutboxMessageFactory` payload 확장(기존 테스트 갱신).
- **Step 2 — 적재**: `RankingScorePolicy`/`RankingKeyResolver`(단위) → `RankingRepository`+`RankingRedisRepository`(SETNX·ZINCRBY·EXPIREAT) → `RankingEventMapper`/`RankingService` → `RankingConsumer`(별도 그룹, 통합).
- **Step 3 — 조회**: `RankingQueryRepository`+구현 → `GetRankingsUsecase`(aggregation) → `RankingV1Controller` → 상품 상세 rank 확장.
- **Step 4 — 검증·마감**: E2E 흐름 테스트, `.http` 예시, 문서(가중치 운영 노트).

## 10. 미결 / 리스크

- `unitPrice` 전달을 위해 `PaymentSucceededEvent` 발행 지점(주문/결제 플로우)에서 아이템 가격 접근 방법은 구현 시 확인(주문 아이템에 가격 보유 예상).
- 랭킹 조회용 Redis 템플릿(master vs replica)은 기존 `RedisConfig` 구성 확인 후 결정 — 조회는 약간의 랙 허용 가능.
- 삭제된 상품 스킵으로 페이지 항목 수가 요청 size보다 작아질 수 있음 — 보정(초과 fetch)은 YAGNI로 제외, 문서화만.
- 다중 인스턴스 컨슈머는 파티션 분배로 자연 분산(SETNX dedup은 인스턴스 무관). hourly 키 메모리는 TTL 2시간으로 자연 정리.
