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

## 결과 (실행 후 기입)

| 시나리오 | 조회 평균 | 조회 최대 | 배치 소요 | 관찰 |
|---|---|---|---|---|
| 0 baseline | | | — | |
| A 제자리 UPDATE | | | | |
| B 사전 INSERT | | | | |

## 판정 기준

- B의 조회 열화가 A보다 유의하게 작으면 → 사용자 가설 채택: RDB로 가더라도 "제자리 감쇠"가 아니라 "사전 시딩" 구조가 맞음 (ZSET carry-over와 동일 결론)
- 둘 다 열화가 크면 → 자정 배치 자체가 서빙 DB에서 못 돌 일 → 읽기 스토어 분리(ZSET) 근거 강화
