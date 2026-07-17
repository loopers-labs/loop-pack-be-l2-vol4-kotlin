# EXP-01 — 자정 배치 × 조회 경합 실측

> 질문: **자정 배치(점수 감쇠/carry-over)가 도는 동안 랭킹 조회는 얼마나 아픈가?**
> 가설(사용자, 2026-07-14): 미리 내일 행을 만들어두는 방식(모델 B)은 조회와 다른 행에 쓰므로 영향 미미.
> 제자리 전면 UPDATE(모델 A)는 열화. 단 InnoDB MVCC라 SELECT는 행 잠금에 안 막힘 — 열화는 잠금 대기가 아니라 자원 경합(버퍼풀·리두·언두)으로 나타날 것.

## 셋업 (전용 스키마 ranking_exp — loopers 스키마 보호)

```bash
docker exec -i docker-mysql-1 mysql -uroot -proot < 01-schema.sql
docker exec -i docker-mysql-1 mysql -uroot -proot < 02-seed.sql   # 10만 행, 수 초
```

## 조회 부하 (mysqlslap — 반드시 --no-drop)

오늘 판 Top-20 조회 = `INDEX(ranking_date, score DESC)`를 타는 인덱스 리드:

```bash
docker exec docker-mysql-1 mysqlslap -uroot -proot \
  --create-schema=ranking_exp --no-drop \
  --concurrency=32 --number-of-queries=200000 \
  --query="SELECT product_id, score FROM product_ranking_daily WHERE ranking_date = CURDATE() ORDER BY score DESC LIMIT 20"
```

## 시나리오

| # | 조건 | 실행 |
|---|---|---|
| 0 | baseline — 배치 없음 | mysqlslap 단독 → 평균 응답 기록 |
| A | 조회 부하 중 모델 A 배치 | mysqlslap 도는 동안 다른 터미널에서 `03-batch-a-decay.sql` (time 측정) |
| B | 조회 부하 중 모델 B 배치 | 동일하게 `04-batch-b-carryover.sql` |

- 각 시나리오 사이 초기화: `TRUNCATE` 후 재시드 (A는 점수를 바꾸고 B는 행을 늘리므로)
- 기록: mysqlslap 평균/최대 응답 · 배치 소요 시간 · (선택) `SHOW ENGINE INNODB STATUS` 락/대기

## 결과 (2026-07-14 실측 — 10만 행 · conc32 · slap 30k 쿼리 · 로컬 도커 arm64)

> slap = mariadb:11 `mariadb-slap`(전용 유저 `exp`, `--skip-ssl`) · 샘플러 = 단발 Top-20 쿼리 60회 왕복 ms (docker exec 오버헤드 포함 — 상대 비교용)

| 시나리오 | slap 30k 총시간 (qps) | 배치 소요 | 샘플러 p50 / p95 / max |
|---|---|---|---|
| 0 baseline | 39.9s (752 qps) | — | 144 / 218 / 249 ms |
| A 제자리 UPDATE | **67.3s (446 qps, −41%)** | **24.1s** | 173 / 242 / **382** ms |
| B 사전 INSERT | 44.2s (679 qps, −10%) | **3.9s** | 166 / 238 / 251 ms |

### 판정 — 가설 채택

- **B(사전 시딩)가 압도**: 조회 열화 −10% vs −41%, 배치 소요 6배 차이(3.9s vs 24.1s), 꼬리 지연(max)도 baseline 수준 유지.
- **왜 A가 비싼가**: score 제자리 변경 = `(ranking_date, score DESC)` 보조 인덱스에서 10만 엔트리 전부 삭제+재삽입(정렬 위치 이동) + 언두/리두 부담. 같은 인덱스 페이지를 읽는 조회와 자원 경합. B는 **다른 날짜 프리픽스 구간에 append**라 오늘 판 인덱스를 건드리지 않음.
- **예측대로 잠금 블로킹은 없었음**(MVCC — 조회 중단 0회). 열화는 전부 자원 경합 형태 — "SELECT는 안 막히니 괜찮다"가 아니라 처리량 −41%로 나타남.
- **설계 결론**: 날짜 경계 처리는 "제자리 감쇠"가 아니라 **사전 시딩(다른 판에 쓰기)** 구조가 맞다 — ZSET carry-over(23:50 사전 생성)와 동일한 결론이 RDB에서도 실측으로 재현됨.

### 재현

```bash
./run-scenario.sh 0 /tmp/exp01-s0   # baseline
./run-scenario.sh A /tmp/exp01-sA   # 제자리 UPDATE
./run-scenario.sh B /tmp/exp01-sB   # 사전 INSERT
```
