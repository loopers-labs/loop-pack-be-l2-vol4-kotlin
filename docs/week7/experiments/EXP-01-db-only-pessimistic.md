# EXP-01 — DB-only 변형 A (비관적 락 `SELECT ... FOR UPDATE`)

> Phase 1 베이스라인. 현재 코드(동기 발급)의 부하 실측 = "DB-only 구조적 상한"의 첫 숫자.
> 기록 포맷: PLAN.md §10 고정 5섹션.

## 1. 조건 (커밋·설정·스펙)

- **코드**: `feature/week07-event-driven` @ `6ec03a8`. 발급 경로 = `CouponService.issue` → `findByIdForUpdate`(`SELECT ... FOR UPDATE`) → `Coupon.issue(now)` 증가 → `UserCoupon` insert → COMMIT. 최종 방어 `uk_user_coupon`.
- **SUT**: AWS EC2 `m5.xlarge` (4 vCPU / 15 GiB), Ubuntu 24.04, ap-northeast-2a. commerce-api(`-Xms1g -Xmx1g`, profile `local`) + mysql:8.0 단일 노드, `load-test/sut-compose.yml` core 스택. 전 포트 127.0.0.1 바인딩.
- **앱 설정**: Tomcat max threads 200 / accept-count 100. Hikari 풀 **40**, connectionTimeout **3000ms** (S1 에러 로그 `total=40 ... request timed out after 3000ms`가 근거). 가상 스레드 off.
- **부하 경로**: k6 v2.0.0을 **SUT와 같은 EC2에 co-located**, `127.0.0.1:8080` 직접 호출. 인터넷 왕복 지연을 decision_ms에서 제거(맥→서울 EC2 경로 배제). 근거 = 하네스 천장 스모크(아래).
- **하네스 천장 스모크**(사전): actuator liveness에 도착률 500→4000/s 램핑 → **실패 0%, p99 19.7ms, dropped 0** (114,967 req). 천장 ≥ 4000/s 이고 SUT 포화점(~250/s)의 **16배** → 하네스는 병목 아님, co-located 유효.
- **시드**: `coupon-perf-seed.sql` — cpuser00001~10000(1만), 쿠폰 90001(한도 100)·90002(한도 10만).

## 2. 가설

- 단일 hot row에 `SELECT ... FOR UPDATE`가 걸려 전 요청이 **직렬화**된다. 락을 트랜잭션 전체 구간 동안 쥐고 그동안 Hikari 커넥션도 점유 → 스파이크에서 커넥션 풀이 먼저 고갈될 것.
- CPU가 아니라 **락 대기 + 커넥션 점유**가 병목 → 처리량은 수백 rps 대에서 꺾이고, 앱/DB CPU는 포화되지 않는다.
- 정합성(발급 ≤ 100 · userId 중복 0)은 락 + unique로 **부하·에러 중에도** 유지된다.

## 3. 수치

### S1 스파이크 — 쿠폰 90001(한도 100), 목표 1000/s × 10s = 1만, distinct 유저

| 지표 | 값 |
|---|---|
| coupon_issued | **100** (정확히 한도) |
| coupon_rejected | 3,256 (SOLD_OUT) |
| coupon_unexpected | **1.38%** (47 / 3,403) — HTTP 500 |
| http_reqs (실발사) | 3,403 / 10,000 · 실효 **~219/s** |
| dropped_iterations | 6,597 (도착률 못 채움) |
| decision_ms | avg 6.28s · med 6.8s · p95 7.89s · **max 8.68s** |

**정합성(DB)**: `issued_quantity=100`, `granted=100`, `distinct_users=100` → 과발급 0 · 중복 0 ✅ (500이 나는 중에도 유지)
**47건 정체**: `SQLTransientConnectionException: Connection is not available, request timed out after 3000ms (total=40, active=40, idle=0, waiting=159)` → `InternalServerException`(COMMON:INTERNAL_ERROR, 500).

### S2 계단 — 쿠폰 90002(한도 10만), 100→200→400→800/s (stage 1m)

| 지표 | 값 |
|---|---|
| coupon_issued | 10,000 (전원 1회 발급) |
| coupon_rejected | 55,767 (재요청 ALREADY_ISSUED) |
| coupon_unexpected | **0.00%** (0 / 65,767) |
| http_reqs | 65,767 · 실효 **~270/s** |
| dropped_iterations | 3,231 |
| decision_ms | avg 599ms · **med 9.74ms** · p90 2.59s · p95 3.35s · max 4.45s |

### 자원 (S2 중간, 400/s 부근 `docker stats --no-stream`)

| | CPU | 해석 |
|---|---|---|
| commerce-api | **79%** (≈0.79 / 4 코어) | 포화 아님 |
| mysql | **27%** (≈0.27 / 4 코어) | 포화 아님 |
| host load avg | 2.35 / 4 | 여유 |

## 4. 병목 판정

- **병목 = 단일 행 X락 직렬화 + 락 구간의 커넥션 점유. CPU 아님.** api 0.79 / mysql 0.27 코어(4 중) → 진단 사다리 ④(MySQL CPU)·⑤(앱 CPU) **배제**, ②(Hikari pending)·③(row lock waits) **지목**. 스레드는 태우는 게 아니라 락을 기다리며 blocked.
- **처리량 천장 ≈ 220~270 req/s.** 결과가 발급이든 SOLD_OUT이든 ALREADY_ISSUED든 전부 같은 쿠폰 행 X락에 직렬화되므로 상한이 동일. 무릎은 200~400/s 사이.
- **부하 형태로 갈리는 실패 모드(같은 뿌리)**:
  - 스파이크(즉시 1000/s): 40 커넥션이 동시에 락 대기 → 159개 큐잉 → 3s connectionTimeout → **500 1.38%**, med 6.8s.
  - 점증(계단): 꼬리 지연이 3s 타임아웃 직전(p95 3.35s)까지만 오르고 k6가 부하를 흘림(dropped) → **500 0%**, med는 9.74ms로 낮게 유지.
- **락 획득 지점 = `SELECT ... FOR UPDATE`** (UPDATE 대기가 아님). 락을 tx 전체(SELECT→앱 로직→insert→COMMIT) 동안 보유 → 변형 B(조건부 원자 UPDATE)가 줄일 수 있는 건 **락 보유 시간**이지 직렬화 자체가 아니다.
- **10초 게이트**: 완료분만 보면 S1 max 8.68s·S2 max 4.45s로 <10s이나, 스파이크에서 47건 500 + 6,597건 미발사(결정 못 받음) → **동기 설계로는 스파이크에서 결정을 보장 못 하고 shed/error로 회피.** 게이트의 취지(모든 요청이 10초 내 확정)는 미충족.

## 5. 결론 · 다음 결정

- 변형 A 정합성은 완벽(과발급·중복 0), 성능은 단일 행 직렬화로 **~250 req/s**에서 붕괴. 커넥션을 락 구간 내내 쥐는 설계라 스파이크에서 커넥션 풀 고갈(500)이 **처리량 붕괴보다 먼저** 온다.
- **다음(변형 B, 조건부 원자 UPDATE)**: `UPDATE coupon SET issued_quantity=issued_quantity+1 WHERE id=? AND issued_quantity < total_quantity` affected-rows 판정. 락 보유 구간을 UPDATE 한 문장으로 축소 → 500 폭풍은 사라지고 천장 RPS 상승 예상. **단 단일 hot row 직렬화는 그대로** → S1 스파이크의 근본 한계는 여전. (가설로 EXP-02에서 검증)
- **변형 C(낙관 @Version)**: 극단 경합에서 재시도 폭풍 예상 — 반례 확보용 1회 측정.
- **Phase 2 진입 근거 선확보**: 병목이 락/커넥션 대기(CPU 아님)로 실측됨 → 가상 스레드 on/off는 "대기 지점만 Tomcat→Hikari로 이동, 총 처리량 거의 불변" 가설을 이 데이터 위에서 검증.
- **근본 해결 방향**: 경합을 DB 단일 행에서 떼어내야 함 → Phase 3(Redis 원자 카운터로 선판정) / Phase 4(Kafka 단일 파티션 순차). DB-only 튜닝의 천장을 B/C·Phase 2로 확정한 뒤 진입.

---

### 부기 — 측정 환경 메모

- co-located k6 선택: 이 실험의 게이트가 time_to_decision(초 단위)이라 맥→서울 EC2 인터넷 왕복이 decision_ms를 오염시킴. 스모크로 하네스 천장(≥4000/s)이 SUT 포화점의 16배임을 확인해 co-located의 CPU 경합 우려를 배제.
- ddl-auto=create라 api 재기동 시 시드 재적재 필요(RUNBOOK §2). 본 런은 재기동 없이 S1→S2 연속.
- EC2는 측정 후 terminate 예정(공유 계정, 격리 SG). 재현 스펙은 위 §1로 고정.
