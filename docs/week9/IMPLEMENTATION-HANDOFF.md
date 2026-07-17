# Week 9 구현 핸드오프 — 다음 세션용

> 작성 2026-07-17. **설계는 끝났고 구현이 안 된 부분**을 다음 세션이 바로 착수하도록 추린 실행 목록.
> 원칙: 각 Phase는 "먼저 읽기(설계 문서) → 닫을 결정 → 만들/바꿀 파일 → 재사용 → DoD → 명령" 순. **TDD**(실패 테스트 1개 → 최소 구현 → green 후 refactor), 실행 전 사용자 confirm.

## 현재 상태 (코드 확인 2026-07-17)

| 항목 | 상태 | 근거 |
|---|---|---|
| Stage 1 MySQL (`product_ranking_daily`, dual write) | ✅ 구현·테스트 green | 브랜치 `feature/week09-ranking-stage1-mysql` |
| Stage 3 Redis ZSET base (Repository + Lua) | ❌ 미구현 | ranking에 ZSet/Redis/Lua 코드·`.lua` 없음 |
| tail 조건부 적재 (carry 소비측) | ❌ 미구현 | — |
| carry 스케줄러 (23:50 스냅샷 + 00:00 병합) | ❌ 미구현 | streamer에 `@EnableScheduling`·Scheduler 없음 |
| Stage 3 측정 자산 (redis-cli 시드·정산) | ❌ 미작성 | `load-test/`엔 MySQL판(`perf-seed-ranking.sql`·`verify-ranking.sql`)만 존재 |
| 06 event store / DLQ / 알림 | ❌ 미구현(설계만) | eventstore/dlq/NotificationSender 코드 없음 |

> Stage 1(MySQL)은 dual write, Stage 3(Redis)는 **23:50 스냅샷 + tail 병합**으로 carry 방식이 저장소와 함께 바뀜(2026-07-17 결정, `05 §6-D3`).

## 착수 전 닫을 결정 (`05 §6`)

- **D1 멱등 장부 위치** — 추천 B(Redis Lua 통합). 단 "재소비 재구축"은 복합키로 A에서도 가능해 **B만의 이점 아님** → 원자 블록 vs 정산 방식으로 판단.
- **D2 MySQL 경로** — ✅ 교체(A) 확정.
- **D3 carry** — ✅ 스냅샷+tail 확정.
- **D4 순위 의미론** — 추천 A(ZCOUNT+1, 동점 동순위 계약 보존).

---

## Phase 0 — 브랜치 분기

```bash
cd /Users/won/Coding/Plan/loopers/loop-pack-be-l2-vol4-kotlin
git checkout feature/week09-ranking-stage1-mysql
git checkout -b feature/week09-ranking-stage3-redis
make init            # pre-commit(ktlintCheck) 훅
docker-compose -f ./docker/infra-compose.yml up -d   # redis/kafka/mysql
```

**세션 프롬프트**
> Week 9 Stage 3 Redis ZSET 구현을 시작한다. `docs/week9/05-stage3-redis-design.html`의 §6 열린 결정(D1 멱등 위치·D4 순위 의미론)을 먼저 확정하고, D2=교체·D3=스냅샷+tail은 확정본으로 진행한다. Phase 1부터 TDD로 한 칸씩 닫는다.

---

## Phase 1 — Stage 3 ZSET base (carry 없이 먼저 green)

**목표**: per-event 오늘 키 단일 적재 + 읽기 경로를 ZSET으로 교체. carry는 Phase 2·3.

**먼저 읽기**: `05 §2`(쓰기 시퀀스) · `§4`(소비 판정·Lua) · `§5 ①키 ②Lua ③읽기 ④서비스`

**만들/바꿀 파일**
- 신규 `apps/commerce-streamer/.../ranking/infrastructure/RankingZSetRepository.kt` + `apps/commerce-streamer/src/main/resources/ranking-accumulate.lua`
- 수정 `apps/commerce-streamer/.../ranking/application/RankingAccumulateService.kt` — 저장소 교체, `@Transactional` 제거(원자성은 Lua로)
- 신규 `apps/commerce-api/.../ranking/infrastructure/RankingZSetReader.kt` — ZRANGE REV · ZSCORE · ZCOUNT
- 수정 `apps/commerce-api/.../ranking/application/RankingQueryService.kt` — MySQL 조회 → ZSET (시그니처·`RankingPageInfo` 불변)

**재사용**
- `apps/commerce-api/.../queue/infrastructure/redis/OrderQueueRepository.kt` — `DefaultRedisScript` + `@Qualifier(REDIS_TEMPLATE_MASTER)` 정석
- `apps/commerce-api/.../coupon/infrastructure/redis/CouponIssueGatekeeper.kt` — Lua `SET NX` 게이트키퍼
- `modules/redis/.../config/redis/RedisConfig.kt` — master(쓰기)/replica(읽기) qualifier (수정 금지, 참조만)

**DoD** (`05 §9`): 이벤트 1건 소비 → `ranking:all:{today}` ZINCRBY 반영 + Testcontainers 통합 테스트 1개 green. 이어 streamer 9 / api 9 테스트 이관.

**명령**
```bash
./gradlew :apps:commerce-streamer:test --tests '*RankingAccumulate*'
./gradlew :apps:commerce-api:test --tests '*Ranking*'
./gradlew ktlintFormat
```

---

## Phase 2 — tail 조건부 적재 (carry 소비측)

**목표**: 벽시계 23:50~00:00일 때만 `tail:{오늘}`에도 ZINCRBY.

**먼저 읽기**: `05 §4`(Lua 노트 ③) · `§5 ②Lua`(ARGV `[4] tail 기록 여부`)

**바꿀 파일**: `ranking-accumulate.lua`(tail 조건부 ZINCRBY + tail EXPIREAT) · `RankingAccumulateService.kt`(now ∈ [23:50,00:00) 판정 → 플래그/키 전달)

**DoD**: 23:50~00:00 이벤트만 tail에 적재, 그 외 시간엔 tail 미생성 — 통합 테스트(시각 주입).

```bash
./gradlew :apps:commerce-streamer:test --tests '*RankingAccumulate*'
```

---

## Phase 3 — carry 스케줄러 (23:50 스냅샷 + 00:00 병합)

**목표**: 콜드 스타트·10분 누락 창 해소.

**먼저 읽기**: `05 §5 ②-b`(스케줄러 스케치) · `§6-D3`(멱등 가드·잔여 갭)

**만들/바꿀 파일**
- streamer 앱 클래스에 `@EnableScheduling` 추가 (**현재 없음**)
- 신규 `apps/commerce-streamer/.../ranking/scheduler/RankingCarryScheduler.kt`
  - 23:50 `ZUNIONSTORE ranking:all:{D+1} 1 ranking:all:{D} WEIGHTS 0.1` (덮어쓰기=멱등)
  - 00:00 `SET carry:merged:{D+1} 1 NX` 성공 시에만 `ZUNIONSTORE ranking:all:{D+1} 2 ranking:all:{D+1} tail:{D} WEIGHTS 1 0.1` → `DEL tail:{D}`

**재사용**: `@Scheduled` 패턴 = `apps/commerce-api/.../queue/application/OrderQueueAdmissionScheduler.kt` · `outbox/application/OutboxRelay.kt`(cron/fixedDelay 형태만; 스케줄러 본체는 streamer에 둠)

**DoD**: ① 23:50 스냅샷 후 내일 키 = 오늘×0.1 ② 00:00 병합 재실행 시 이중 가산 없음(가드) — 통합 테스트 각 1개 green.

```bash
./gradlew :apps:commerce-streamer:test --tests '*RankingCarry*'
```

---

## Phase 4 — 정리 + 과제 검증 3종

**목표**: D2 확정대로 Stage 1 MySQL 경로 제거 + E2E.

**바꿀 파일**: `product_ranking_daily` 엔티티·리포지토리(streamer·api) 제거(Replace, Don't Deprecate). *(측정 대조는 커밋 `6744aca` 체크아웃으로 수행 — 코드 남기지 않음)*

**DoD** (`05 §8`): E2E 3종 — 발행→적재→조회 / 일자 변경 후 전일 조회(+ 스냅샷·tail 웜 스타트) / 가중치 순서(주문 1건 0.7 > 좋아요 3건 0.6).

```bash
./gradlew :apps:commerce-streamer:test :apps:commerce-api:test
./gradlew build
```

---

## Phase 5 — Stage 3 측정 자산 (본런은 별도 승인)

**목표**: `05 §8` 측정 자산 변경분. **이 플랜 승인이 측정 실행 승인은 아님**(review-before-execute).

- 신규: `perf-seed-ranking.sql` 동치 분포를 `redis-cli --pipe` ZADD로 적재하는 스크립트
- `verify-ranking.sql` → redis-cli 기반(ZCARD·표본 score·**스냅샷+tail 후 내일 ×0.1 비율**·handled 키 수), **double 오차 ε 허용**

---

## 별도 트랙 — 06 event store / DLQ (Stage 3 이후)

> `06-event-store-recovery-dlq-design.html`. 복구 전용 원천 저장 + 유실 방지. **Stage 3 완료 후 착수**, 열린 결정(E1 조회 유실 수준·E3 재시도 파라미터) 확정 필요.

**만들 파일(요지)**
- `EventSubscription.EVENTSTORE` 추가 → metrics consumer에 `handleOnce(eventId, EVENTSTORE)`로 원천 저장 얹기(복구 전용)
- DLT consumer(`<topic>-dlt`) → 실패 이력(B) MySQL 적재
- `NotificationSender` 인터페이스 + `LoggingNotificationSender` 구현체까지만
- 발행 구간: outbox `status`/`retry_count` 확장(FAILED 격리 + 알림)
- 저장소: MySQL 먼저

**먼저 읽기**: `06 §3`(유실 방지 발행+소비) · `§4`(원천 저장 A) · `§7`(실패 이력+알림) · `§9`(열린 결정) · `§10`(작업 순서)

---

## 공통 명령 레퍼런스

```bash
./gradlew :apps:<app>:bootRun --args='--spring.profiles.active=local'   # 기동(프로파일 필수)
./gradlew :apps:<app>:test --tests '<pattern>'                          # focused 테스트
./gradlew ktlintFormat && ./gradlew ktlintCheck                         # 포맷
docker-compose -f ./docker/infra-compose.yml up -d                     # 로컬 인프라
```

> PR: `$loopers-pr-workflow` 스킬 경유. 서브 PR base = fork 내 주차 통합 브랜치, 최종만 `loopers-labs` base `shoeone96`. 커밋/PR에 AI 흔적 금지.
