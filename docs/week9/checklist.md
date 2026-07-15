# Round 9 구현 체크리스트 — 실시간 상품 랭킹 (Redis Sorted Set)

> 원문 요구사항: `00-requirements.html`
> 브랜치: `feature/week09-ranking` (origin/shoeone96 에서 분기 — R7 collector + R8 대기열 머지 포함)
> 매핑: 과제 Step 1 = Consumer → ZSET 적재 · Step 2 = Ranking API
> 랭킹은 **쓰기(collector가 이벤트 소비하며 ZSET 점수 갱신)** 와 **읽기(API가 ZSET 조회)** 가 분리된 시스템이다. R7 이벤트 파이프라인의 **소비 지점에 ZSET 갱신을 얹고**, product_metrics 집계는 그대로 둔다.

## 단계적 검증 로드맵 (사용자 결정 2026-07-14 — 바로 ZSET 금지, 측정하며 업그레이드)

> 목적: "왜 ZSET인가"를 문서가 아니라 **실측으로** 확보한다. Stage 1·2는 제출물이 아니라 **타임박스된 실험** — 각 단계의 한계가 측정으로 드러나면 즉시 다음 단계로. 측정 조건 고정: 같은 시드 데이터 · 같은 k6 시나리오(R8 부하테스트 자산 재사용) · 같은 지표(p95/p99 · 최대 RPS · 신선도 · 개별 순위 조회 비용).

- [ ] **Stage 0 — 측정 준비** (30분 컷): 상품 + product_metrics 시드 스크립트(1만/10만 2스케일) · k6 rankings 시나리오 (R8 EXP 재사용)
- [ ] **Stage 1a — MySQL 조회 시점 계산 (방식 A)** (타임박스 2h): 쿼리 안에서 score 합성(`0.1·view + 0.2·like + 0.6·sales`) + `ORDER BY score DESC LIMIT` · 개별 순위 = `1 + COUNT(score식 > 내 score식)`
  - 측정: EXPLAIN(filesort 확인) · 스케일별(1만 vs 10만) p95 · 개별 순위 쿼리 비용
  - 예상 한계(실측으로 확인): ① score가 계산식 → 인덱스 불가, 조회마다 전 행 스캔 ② 개별 순위가 사실상 전 행 2중 스캔 ③ 날짜 차원 없으면 누적 랭킹만 가능(롱테일)
- [ ] **Stage 1b — MySQL 쓰기 시점 score (RDB로 번역한 방식 B, 사용자 설계 2026-07-14)**: 별도 테이블 `product_ranking_daily(ranking_date, product_id, score)` — `UNIQUE(ranking_date, product_id)` + `INDEX(ranking_date, score DESC, product_id ASC)` (3컬럼 확장 2026-07-15 — tie-break filesort 제거, EXPLAIN 근거 WRITING-LOG). `product_metrics`는 R7 소유물이라 오염 금지 → 테이블 분리
  - **구현 완료 ✅ 2026-07-15** (브랜치 `feature/week09-ranking-stage1-mysql`): streamer dual write 적재(테스트 9) + api 랭킹 조회/상세 순위(테스트 9) — 전 테스트 green. 남은 것 = 쓰기 비용 실측(아래)과 k6 측정(사용자 검토 후)
  - 쓰기 = `INSERT ... ON DUPLICATE KEY UPDATE score = score + Δ` (ZINCRBY 대응) · 날짜 경계 = 새 날짜 행 lazy 생성 (일간 키 대응) · carry-over = **dual write: 오늘 행 +Δ와 내일 행 +Δ×0.1 동시 upsert (2026-07-15 결정 — 사전 INSERT 배치 대체)** · 보존 = `DELETE < D-2` 배치 (TTL 대응)
  - 추가 측정 (2026-07-15): **단일 upsert vs dual write(트랜잭션 2행)** 쓰기 경로 처리량·p95 — carry 비용을 숫자로 확보
- [x] **EXP-01 — 자정 배치 × 조회 경합 실측 (사용자 제안) ✅ 2026-07-14 완료**: 10만 행 · conc32 실측 — A(제자리 UPDATE) 조회 **−41%**·배치 24.1s vs B(사전 INSERT) **−10%**·배치 3.9s → **가설 채택: 날짜 경계는 사전 시딩**. MVCC라 블로킹 0회, 열화는 전부 자원 경합(인덱스 10만 엔트리 재배치). 상세·재현: `experiments/exp01/README.md`
- [ ] **Stage 2 — 캐싱으로 (방식 D 계열, 실시간 아님)** (타임박스 1.5h): Stage 1 쿼리 결과를 Redis 캐시 TTL 60s로 페이지 캐싱 (week5/8 캐시 모듈 컨벤션 재사용)
  - 측정: 같은 k6 → p95·RPS 변화 (읽기 비용 붕괴 확인)
  - 예상 한계(실측으로 확인): ① 신선도 = TTL 동안 순위 고정 ② TTL 만료 순간 원 쿼리로 몰림(stampede) ③ **상품 상세의 개별 순위는 페이지 캐시로 해결 안 됨** ④ 페이지·사이즈 조합마다 캐시 키 증가
- [ ] **Stage 3 — ZSET (방식 B, 과제 Must)**: 아래 Step 1·2 본 구현 + 같은 시나리오 재측정
- [ ] **비교표 작성 (→ WRITING-LOG · 블로그 소재)**: 3단계 × {p95, 최대 RPS, 신선도, 개별 순위 비용, 쓰기 경로 부하, 운영 복잡도}. **어떤 요구사항 항목이 Stage 1·2를 탈락시켰는지** 명시 (실시간 반영 · 상품 상세 개별 순위 · 조회 빈도) + **Stage 1·2가 정답인 상황** 명시 (소규모·저빈도 = A / 신선도 요구 낮음·Top-N 페이지만 = 캐시)

## 재사용 자산 (이미 확보 — 새로 만들지 말 것)

## 재사용 자산 (이미 확보 — 새로 만들지 말 것)

| 필요 기능 | 재사용할 기존 자산 | 경로 |
|---|---|---|
| 이벤트 배치 소비 | `ProductMetricsKafkaConsumer` — 이미 `BATCH_LISTENER`, 토픽 `catalog-events`·`order-events`·`user-action-events`, groupId `commerce-streamer-metrics`, manual ack | `apps/commerce-streamer/.../interfaces/consumer` |
| 집계 진입점 | `ProductMetricsService.handle(eventId, eventType, payload)` — 여기 옆에 ZSET 갱신을 얹는다 | `apps/commerce-streamer/.../metrics/application` |
| 멱등 처리 | `EventHandled`(event_id PK) + `EventHandledRepository` | `apps/commerce-streamer/.../metrics/{domain,infrastructure}` |
| Redis ZSET 연산 | `OrderQueueRepository` — `@Qualifier(REDIS_TEMPLATE_MASTER)` + `opsForZSet()`(addIfAbsent/rank/size) + `DefaultRedisScript` Lua 패턴 | `apps/commerce-api/.../queue/infrastructure/redis` |
| 상품 정보 조합 | product 상세 조회 캐시(week5/week8 통합) · `product_metrics` | `apps/commerce-api/.../product` · `commerce-streamer` |
| 배치 스케줄러(Nice: carry-over) | `@Scheduled(fixedDelayString=...)` + `@ConditionalOnProperty` on/off | `outbox/application/OutboxRelay.kt` |
| 테스트 인프라 | Redis Testcontainers 픽스처 + `RedisCleanUp` / `DatabaseCleanup` | `modules/redis/src/testFixtures` · `commerce-api` 테스트 support |

> 가장 강력한 재사용점 = **collector가 이미 배치 리스너로 event 를 멱등 소비**한다는 것. R9는 `product_metrics` upsert와 **나란히 ZSET `ZINCRBY`를 추가**하는 형태 → "Nice-to-Have 배치 리스너"는 이미 확보됨.

## 스펙 고정값 (요구사항)

- **KEY**: `ranking:all:{yyyyMMdd}` (KST 기준 날짜)
- **TTL**: 2Day (윈도우 1.5~2배)
- **집계 단위**: 일간만 (주간/월간은 차주 예고)
- **스코어 델타**: 이벤트별 `Weight × Score` — 아래 (예시, 우리 기준으로 재산정)
  - 조회 `view` : Weight 0.1, Score 1
  - 좋아요 `like` : Weight 0.2, Score 1
  - 주문 `order` : Weight 0.6~0.7, Score `price × amount` (정규화 시 log)
- **API**: `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1` / 상품 상세에 순위 추가

---

## Step 1 — Kafka Consumer → Redis ZSET 적재

- [ ] **1. 날짜별 키 계산** — 이벤트 발생 시각(KST) 기준 `ranking:all:{yyyyMMdd}` 키 산출 기능
- [ ] **2. ZSET 점수 반영** — `ProductMetricsService.handle` 소비 지점에서 이벤트 타입별 `Weight × Score` 를 `ZINCRBY` 로 누적. `masterRedisTemplate.opsForZSet().incrementScore(key, productId, delta)` (R8 `OrderQueueRepository` 패턴)
- [ ] **3. TTL 부여** — 키 최초 생성 시점에 2Day TTL. `INCR` 후 TTL 없으면 붙이기(원자성: Lua 또는 `expire` 조건부 — 매 이벤트 재설정 피함)
- [x] **4. 스코어 모델 확정 ✅ 2026-07-15** — view 0.1 / like 0.2 / 주문 **order line당 0.7 고정**. 가격·수량 미반영(건수 카운트 — 인기 랭킹 업계 표준, 조사 13건 `purchase-signal-research.html`). 주문 1건 0.7 > 좋아요 3건 0.6 충족. (근거 WRITING-LOG)
- [x] **5. 감소 이벤트 정책 ✅ 2026-07-15** — 좋아요 취소 대칭 차감(−0.2), 음수 score 허용. (근거 WRITING-LOG)
- [ ] **원자성/멱등** — 이미 `EventHandled`(event_id) 멱등 위에서 동작하므로 ZSET 갱신도 같은 멱등 경계 안에 둔다. `product_metrics` upsert 와 ZSET 갱신의 **정합성 경계**(같은 트랜잭션/배치 vs 분리) 결정

## Step 2 — Ranking API

- [ ] **6. 랭킹 페이지 조회** — `GET /api/v1/rankings?date=&size=&page=` → `ZREVRANGE`(내림차순) + 페이지네이션. `date` 미지정 시 오늘
- [ ] **7. 상품 정보 Aggregation** — ZSET 은 productId·score 만 → 상품명·가격·브랜드 등 조합해 반환. **N+1 회피**(일괄 IN 조회 / 캐시 재사용)
- [ ] **8. 상품 상세에 순위** — 상품 상세 조회 응답에 `ZREVRANK` 순위 포함. **랭킹에 없으면 null**
- [ ] **9. 미진입/빈 랭킹 응답** — 콜드 스타트 등으로 데이터 없을 때 응답 형태 정의(빈 배열 / null rank)

## 검증 (과제 필수)

- [ ] **E2E 흐름** — 이벤트 발행 → ZSET 점수 반영 → API 조회까지 정상 동작 (Testcontainers Kafka+Redis)
- [ ] **일자 변경** — 날짜가 바뀌어도 이전 날짜(`?date=`) 랭킹 조회가 정상 동작
- [ ] **가중치 반영** — 가중치가 의도대로 순서에 반영 (예: 주문 1건 > 좋아요 3건 — 점수로 검증)

## Nice-To-Have (Must 완료 후에만)

- [ ] **실시간 Weight 조절** — 점수 계산 가중치를 재기동 없이 수정하는 방법 (config 외부화 / 관리 API)
- [ ] **시간 단위(1h) 랭킹** — `ranking:all:{yyyyMMddHH}` 등 초 실시간
- [ ] **콜드 스타트 완화 = dual write carry-over (2026-07-15 결정 — 23:50 스케줄러 대체)** — 이벤트 소비 시 오늘 키 `ZINCRBY +Δ`와 **내일 키 `ZINCRBY +Δ×0.1`을 함께** 실행. 배치 실패 모드 제거 + 누락 창 0, carry가 `EventHandled` 멱등을 상속. 내일 키 TTL은 첫 dual write 시 부여(2Day면 내일 키도 자연 커버). 비용 = 이벤트당 Redis 연산 2배 — Stage 1b에서 단일 vs dual 쓰기 비용 실측. (버린 대안: 23:50 `ZUNIONSTORE` 스케줄러 — WRITING-LOG 2026-07-15 결정 로그 참조)
- [ ] **Top-N 캐싱** — 매 요청 `ZREVRANGE` vs 주기 캐싱 트레이드오프
- [ ] **상위 N만 유지** — 상품 다수 시 ZSET 메모리 관리(`ZREMRANGEBYRANK`)

## 진입 전 확정할 열린 결정 (→ WRITING-LOG)

1. ~~**스코어 모델**~~ ✅ 2026-07-15 확정: view 0.1 / like 0.2 / **주문 order line당 0.7 고정 (가격·수량 미반영 — 건수 카운트, Shopify·Amazon 계열)**. 조사 13건: `purchase-signal-research.html`, 근거: WRITING-LOG
2. ~~**감소 이벤트**~~ ✅ 2026-07-15 확정: 좋아요 취소 **대칭 차감(−0.2)**, 음수 score 허용 (metrics 동작 일치 · ZINCRBY 의미론 보존)
3. **ZSET 갱신 위치** — collector 소비 트랜잭션/배치 안에서 product_metrics와 함께 vs 분리
4. **TTL 부여 방식** — 매 이벤트 재설정 회피(조건부 expire / Lua)
5. ~~**Aggregation**~~ ✅ 2026-07-15 (Stage 1b 구현으로 확정): `productRepository.findAllActiveByIdIn` IN 일괄 — ZSET 전환 시 그대로 재사용
6. ~~**API 응답 계약**~~ ✅ 2026-07-15 (Stage 1b 구현으로 확정): 빈 랭킹 = 빈 배열, 미진입 상세 rank = null(필드 생략), 삭제 상품 = 항목 제외, rank = competition ranking(동점 동순위)
7. **유실·복구 전략** — 이 설계에서 Redis는 캐시가 아니라 **누적 점수의 유일한 저장소**. 오늘 판 유실 시 재구축 경로 결정: Kafka 재소비가 자연스러우나 R7 `EventHandled` 멱등이 재소비를 막음 → 랭킹용 멱등 경계 분리(별도 consumer group) vs 감수(리더보드 정확성 요구 수준 판단) vs Redis persistence 의존. 과거 이력은 TTL로 영구 소실 — "전일 조회"까지만 요구되므로 수용, 이력 요구 생기면 만료 전 DB 스냅샷 배치 (2026-07-14 논의)

## 구현 완료 후 — Technical Writing (제출 필수 산출물)

- [ ] GitHub Issue 4포맷 중 1개 (Design Doc / Retrospective / Challenge Story / Benchmark Report)
- [ ] 블로그 글 — TL;DR 필수, "왜 그렇게 판단했나" 중심

---

## DoD (오늘 오후 닫힌 칸 1개)

> 진입 전 완료조건: **"작동 동작 1개 / 통과 테스트 1개"**. 오늘은 예: *이벤트 1건 소비 → `ranking:all:{today}` ZSET에 가중치 점수 `ZINCRBY` 반영 + 통합 테스트 1개 통과*. 전체 완성이 아니라 쓰기 경로 한 칸을 닫는다.
