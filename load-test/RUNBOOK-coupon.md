# 선착순 쿠폰 부하 측정 런북 (Phase 0-b 산출물)

> 서버(SUT)와 k6가 같은 호스트면 `BASE_URL=http://127.0.0.1:8080` 그대로.
> 홈서버 SUT + 맥 k6 구성이면 맥에서 `ssh -L 8080:127.0.0.1:8080 -L 8081:127.0.0.1:8081 won@<server>` 터널을 열고 동일 URL 사용.
> 전 포트는 SUT에서 127.0.0.1 바인딩(공인 IP 무노출).

## 1. 이미지 빌드 + core 기동 (Phase 1~2, DB-only)

```bash
docker compose -f load-test/sut-compose.yml up -d --build
```

api 준비(스키마 생성 완료)까지 대기 — liveness가 200일 때까지:

```bash
until curl -sf http://127.0.0.1:8081/actuator/health/liveness >/dev/null; do sleep 2; done
echo "SUT ready"
```

## 2. 시드 적재 (⚠️ api 재시작할 때마다 재실행 — ddl-auto=create가 스키마를 리셋)

로컬 mysql 클라이언트가 없으면 컨테이너 경유:

```bash
docker exec -i sut-mysql mysql -uapplication -papplication loopers < load-test/coupon-perf-seed.sql
# 확인
docker exec -i sut-mysql mysql -uapplication -papplication loopers \
  -e "SELECT COUNT(*) accounts FROM account WHERE email LIKE 'cpuser%'; SELECT id,total_quantity,issued_quantity FROM coupon WHERE id IN (90001,90002);"
```

## 3. 하네스 천장 스모크 (본 측정 전 선행 — 경로가 병목이 아님을 확인)

```bash
k6 run load-test/k6/harness-ceiling-smoke.js
```

판정: 도달 가능한 최대 RPS와 그때 p99를 본다. **이 천장이 뒤 측정의 SUT 포화 RPS × 3 이상**이면 하네스는 병목이 아니다 → 그대로 진행. 못 넘으면 경로 교체(터널→localhost k6 등). 근거: `experiments/WRITING-LOG.md` 결정 9-보강.

## 4. S1 스파이크 (한도 100, 10초 내 1만 요청)

```bash
mkdir -p load-test/results   # --summary-export 대상(gitignore됨)
BASE_URL=http://127.0.0.1:8080 COUPON_ID=90001 \
  k6 run --summary-export load-test/results/s1-spike.json load-test/k6/coupon-issue-spike.js
```

정합성 검증(불변식):

```bash
docker exec -i sut-mysql mysql -uapplication -papplication loopers \
  -e "SELECT issued_quantity FROM coupon WHERE id=90001;
      SELECT COUNT(*) granted, COUNT(DISTINCT user_id) distinct_users FROM user_coupon WHERE coupon_id=90001;"
```

- `issued_quantity` = 정확히 100
- `granted` = 100, `distinct_users` = 100 (userId 중복 0)
- k6 `coupon_issued` ≈ 100 / `coupon_rejected` ≈ 9900 / `coupon_unexpected` rate < 0.01

## 5. S2 계단 (한도 10만, 100→200→400→800 req/s)

```bash
BASE_URL=http://127.0.0.1:8080 COUPON_ID=90002 \
  k6 run --summary-export load-test/results/s2-step.json load-test/k6/coupon-issue-step.js
# 짧게 돌리려면 각 단계 단축: STAGE_DUR=1m
```

관찰: 도착률을 올릴 때 throughput이 어느 단계에서 꺾이는지(포화점) + `coupon_decision_ms` p95/p99 상승 지점. 이게 DB-only 구조적 상한.

## 6. 정리

```bash
docker compose -f load-test/sut-compose.yml down          # 컨테이너만
docker compose -f load-test/sut-compose.yml down -v        # 볼륨까지(완전 초기화)
```

## Phase 전환

- Phase 3(Redis): `--profile p3 up -d`. 앱 datasource.redis 는 redis-master/readonly 서비스로 이미 배선(env). Lua 원자 판정 코드는 Phase 3에서 추가.
- Phase 4(Kafka): `--profile p4 up -d`. commerce-api kafka producer 배선은 Phase 4 작업.
