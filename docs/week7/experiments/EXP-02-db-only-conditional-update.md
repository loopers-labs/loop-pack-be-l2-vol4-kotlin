# EXP-02 — DB-only 변형 B (조건부 원자 UPDATE)

> Phase 1 두 번째 변형. 락 보유 구간을 UPDATE 한 문장으로 축소했을 때 DB-only 천장이 어디까지 오르는지 실측.
> 기록 포맷: PLAN.md §10 고정 5섹션. 비교 기준 = EXP-01(변형 A, 비관적 락).

## 1. 조건 (커밋·설정·스펙)

- **코드**: `exp/week07-coupon-variant-b` @ `262960d` (스크래치 브랜치 — 채택 여부는 §5에서 판단). 발급 경로 = `CouponService.issue` → `findById`(무락) → `validateIssuable`(NOT_ISSUABLE/EXPIRED, 불변 컬럼만 사용) → `existsBy` 사전 체크(무락) → **`UPDATE coupon SET issued_quantity=issued_quantity+1 WHERE id=? AND issued_quantity<total_quantity`** (affected rows 0 = SOLD_OUT) → `UserCoupon` insert → COMMIT. `SELECT ... FOR UPDATE` 제거. 최종 방어 `uk_user_coupon`(race를 뚫은 중복 insert → `DataIntegrityViolationException` → advice 409).
- **변형 A와의 차이**: A는 트랜잭션 전 구간(SELECT FOR UPDATE→앱 판단→existsBy→insert→COMMIT, 왕복 4~5회) X락 보유. B는 UPDATE 한 문장부터 COMMIT까지만 보유. 매진 후 거절 요청도 affected-0 UPDATE가 hot row X락을 스침(MySQL RR은 조건 불일치 행도 락) — 직렬화 자체는 잔존.
- **SUT**: EXP-01과 동일 — AWS EC2 `m5.xlarge`(4 vCPU / 15 GiB), Ubuntu 24.04, ap-northeast-2a. commerce-api(`-Xms1g -Xmx1g`, profile `local`) + mysql:8.0, `load-test/sut-compose.yml` core 스택, 전 포트 127.0.0.1 바인딩. 이미지 EC2 네이티브 리빌드(x86_64).
- **앱 설정**: EXP-01과 동일 — Tomcat max threads 200 / Hikari 풀 40, connectionTimeout 3000ms, 가상 스레드 off.
- **부하 경로**: k6 co-located, `127.0.0.1:8080` 직접 호출. 하네스 천장 스모크는 EXP-01 실측(≥4000/s = 포화점의 16배) 재사용 — 동일 인스턴스·동일 경로.
- **시드**: `coupon-perf-seed.sql` — cpuser00001~10000(1만), 쿠폰 90001(한도 100)·90002(한도 10만). 리빌드 후 재적재.
- **정합성 회귀**: `*Coupon*` 테스트 43개 green (300-스레드 동시성 포함 — 정확히 100건·중복 0).

## 2. 가설 (EXP-01 §5에서 이월)

- **500 폭풍(Hikari 고갈)은 사라진다**: A의 S1에서 500 1.38%는 락을 쥔 채 커넥션을 3초 이상 점유해 풀 40이 고갈된 결과. B는 커넥션 점유 시간이 UPDATE 한 문장 수준으로 줄어 풀 고갈 전에 트랜잭션이 빠진다.
- **천장 RPS는 상승한다**: 직렬화 단위가 "트랜잭션 전체"에서 "UPDATE 한 문장"으로 줄어 hot row 점유 시간이 짧아짐 → 시간당 통과량 증가. A의 ~250 req/s 대비 상승 예상.
- **단, 단일 hot row 직렬화는 그대로**: 모든 요청(발급·매진 거절 포함)이 여전히 같은 행의 X락을 직렬로 통과 → 천장은 오르되 유한. S1 스파이크(순간 1000/s)의 근본 한계는 여전할 것.
- **불변식(전 variant 공통)**: 발급 ≤ 100 / userId 중복 0 / 거절도 조회 가능 / decision ≤ 10초 — 부하·에러 중에도 유지.

## 3. 수치 (A = EXP-01 대비)

### S1 스파이크 — 쿠폰 90001(한도 100), 목표 1000/s × 10s, distinct 유저

| 지표 | A (비관적 락) | B (조건부 UPDATE) |
|---|---|---|
| coupon_issued | **100** | **100** |
| coupon_rejected | 3,256 | 3,379 |
| coupon_unexpected (HTTP 500) | **1.38%** (47) | **0.08% (3)** |
| http_reqs (실발사) | 3,403 · ~219/s | 3,482 · ~210/s |
| dropped_iterations | 6,597 | 6,519 |
| decision_ms | avg 6.28s · med 6.8s · p95 7.89s · max 8.68s | avg 6.39s · med 7.5s · p95 9.06s · **max 10.2s** |

- **정합성(DB)**: `issued_quantity=100`, `granted=100`, `distinct_users=100` — 과발급 0 · 중복 0 ✅
- **500 3건의 정체**: api 로그 실확인 — A와 동일한 `Connection is not available, request timed out after 3000ms (total=40, active=40, waiting=159)`. 뿌리(풀 40 vs 스파이크 동시성)는 같고 발생 빈도만 47→3으로 급감.
- 양쪽 모두 k6 `Insufficient VUs (maxVUs=2000)` 도달 — S1 실발사량은 하네스 VU 상한 × 응답 지연의 함수. 동일 조건 상대 비교는 유효하나 "서버 순수 천장"은 아님.

### S2 계단 — 쿠폰 90002(한도 10만), 100→200→400→800/s (stage 1m)

| 지표 | A | B |
|---|---|---|
| coupon_issued | 10,000 | 10,000 |
| coupon_rejected | 55,767 | 56,489 |
| coupon_unexpected | 0.00% | 0.00% |
| http_reqs | 65,767 | 66,489 |
| dropped_iterations | 3,231 | **2,507** |
| decision_ms | avg 599ms · med 9.74ms · p90 2.59s · p95 3.35s · max 4.45s | avg **353ms** · med 8.01ms · p90 **1.48s** · p95 **2.19s** · max 5.71s |

- **정합성(DB)**: `issued_quantity=10000`, `granted=10000`, `distinct_users=10000` ✅
- **B 처리량 타임라인** (k6 진행 로그 10초 간격 완료 수 차분 — 이번 런부터 보존):
  - 0~3m (100→400/s 램프): 도착률을 **전 구간 추적** (110→193→253→387/s)
  - 3m~3m30s (400→600/s 램프): 여전히 추적 (440→506→569/s)
  - 3m30s~4m (600→800/s 램프): **~550~590/s에서 포화** — drops 2,507이 이 구간에 집중
  - → **B의 S2 무릎 ≈ 550~590 req/s**
- A는 진행 로그 미보존이라 동일 타임라인 비교 불가 (아래 §4 참고).

### 자원 (S2 중 `docker stats --no-stream`)

| | A (400/s 부근) | B (시작 +150s, ~250~300/s 구간) |
|---|---|---|
| commerce-api | 0.79 코어 | **2.25 코어** |
| mysql | 0.27 코어 | **0.94 코어** |
| host load avg (1m) | 2.35 | **13.25** |

- B의 2번째 스냅샷(800/s 구간 목표)은 k6 종료 후에 찍혀 **무효** — 800/s 구간 자원 실측은 공백 (한계로 기록).

## 4. 병목 판정

- **가설 1 (500 폭풍 소멸) — 대체로 적중**: Hikari 고갈성 500이 47→3건 (S1). 커넥션 점유 시간이 UPDATE 한 문장 수준으로 줄어 풀 40이 고갈 직전까지만 감. 단 완전 소멸은 아님 — 뿌리(스파이크 동시성 ≫ 풀 40)는 남아 있고, 그 증거로 `waiting=159` 큐잉이 여전히 관측됨.
- **가설 2 (천장 상승) — S2에서 적중, S1에서는 무의미**: S2 무릎이 타임라인 실측 ~550~590/s로, A 시절 추정(200~400/s 사이)을 크게 상회. 반면 S1 실효 처리량은 A≈B(~210~220/s)로 차이 없음 — S1의 지배 변수는 변형 코드가 아니라 **대기 피드백 루프**(응답 수 초 → VU 고갈 → 발사량 제한)라서, 동기 API인 이상 변형을 바꿔도 스파이크 실효 처리량이 안 움직임.
- **가설 3 (직렬화 잔존) — 적중, 단 형태가 바뀜**: A는 "락 대기"(스레드 blocked, CPU 유휴 — api 0.79/mysql 0.27 코어, load 2.35)였는데, B는 같은 구간에서 **CPU 소모형**(api 2.25/mysql 0.94 코어, load 13.25)으로 전환. 직렬화 병목이 풀리자 박스가 실제로 일을 하기 시작 — **병목이 "hot row 락 대기"에서 "박스 CPU(앱+MySQL+co-located k6 합산)"쪽으로 이동**. 진단 사다리로는 ②③(락·커넥션 대기) 중심에서 ④⑤(CPU) 후보로 이동. 정밀 판정(어느 컴포넌트가 먼저 포화인지)은 Phase 2의 몫.
- **co-located 경계 도달 조짐**: load avg 13/4코어는 k6 자체가 SUT와 본격 경합 중이라는 신호. WRITING-LOG 결정 11의 예고("더 높은 RPS에서 co-located 여유를 잠식하면 부하생성기 분리")가 B의 ~600/s 영역에서 현실화 — **B의 무릎 ~550~590/s는 "co-located 포함 하한 추정치"로 읽어야 함**. 변형 간 상대 비교(1차 결론)는 유효하나, 절대치 발표용으로 쓰려면 부하생성기 분리 재측정 필요.
- **10초 게이트**: B도 미충족 — 형태만 변화. A는 "에러(500)로 회피", B는 "대기로 버팀"(max 10.2s로 완료분조차 게이트 스침 + 6,519건 미발사). **동기 설계 자체가 스파이크에서 결정 보장 불가**라는 결론은 변형과 무관하게 유지.
- **EXP-01 해석 정정 (기록의 정직성)**: EXP-01의 "천장 ≈ 220~270 req/s"에서 S2 쪽 ~270/s는 **램프 스케줄의 평균 도착률과 일치하는 값**이라 천장의 증거가 못 된다 (A도 66k/69k를 완료 — 스케줄을 거의 소화했음). A의 S2 무릎은 지연 급등(p95 3.35s) 기반 추정일 뿐 타임라인 실측이 없다. → EXP-01의 천장 문구는 "S1 스파이크 하 실효 처리량"으로 한정해 읽을 것. 이번 런부터 k6 진행 로그를 보존해 타임라인 차분으로 무릎을 실측하는 것을 표준 절차로 승격.

## 5. 결론 · 다음 결정

- **정합성은 B에서도 완벽** (발급 정확히 100/10,000 · 중복 0, 부하·에러 중 유지). 조건부 원자 UPDATE + `uk_user_coupon` 2겹 방어가 락 없이 동일한 정합성을 담보.
- **B는 DB-only의 뚜렷한 개선**: 500 폭풍 94% 감소, S2 꼬리 지연 35~40% 감소(p95 3.35→2.19s), 무릎 ~550~590/s (A 추정 대비 ~2배±). **같은 MySQL, 코드 6파일 수정만으로** — "Redis 전에 조건부 UPDATE부터"라는 반박에 대한 실측 답변.
- **그러나 스파이크는 못 구한다**: S1 실효 처리량·decision 꼬리는 A와 동급(max는 10.2s로 게이트 브리치). 순간 1000/s 앞에서는 락을 줄이든 말든 동기 요청-응답 모델이 대기 폭탄이 됨 → **Phase 4(Kafka 접수-후-확정)의 존재 이유가 B의 실측으로 더 선명해짐**.
- **코드 채택**: 변형 B 코드는 `exp/week07-coupon-variant-b`에 유지. Phase 1 종료(C 측정 후) 시점에 프로덕션 경로 채택 여부 결정 — Phase 3(Redis) 설계가 "DB 조건부 UPDATE + unique 최종 방어"를 방어선으로 전제하므로 채택 가능성 높음.
- **다음**: ① 변형 C(낙관 @Version + 제한 재시도) 측정 — 극단 경합 재시도 폭풍 반례 확보용 ② Phase 2 진단 사다리에서 B 기준 병목(④ MySQL CPU vs ⑤ 앱 CPU vs co-located k6 경합) 분리 판정 ③ 절대치 확정이 필요해지면 부하생성기 별도 노드 분리.

---

### 부기 — 측정 환경 메모

- EC2 레포가 `--single-branch` clone이라 `git fetch origin`이 새 브랜치를 못 가져옴 → `git fetch origin <branch>:<branch>` 특정 ref fetch로 해결 (측정 절차에 기록).
- 측정 실행은 상세 런북 프롬프트 기반 경량 모델 서브에이전트로 위임 (코드/분석/기록은 본 세션). 스냅샷 타이밍(원격 sleep 누적 오차)이 계획과 어긋나 스냅샷2가 무효가 된 것이 위임 방식의 한계로 관측됨 — 다음 런은 k6 시작 시각 기준 절대 시각 스케줄로 개선.
- raw: `load-test/results/ec2-variant-b-*.{json,log}` (gitignore, 로컬 보존). EC2 인스턴스는 변형 C 측정이 남아 유지.
