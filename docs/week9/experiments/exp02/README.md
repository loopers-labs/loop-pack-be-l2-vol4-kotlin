# EXP-02 — Stage 1a "조회 시점 계산" 읽기 비용 실측 (로컬 파일럿)

> 2026-07-16 · 커밋 6744aca · 로컬 docker mysql 8.0 + mariadb-slap conc32 · 원본: `load-test/results/exp02-stage1a/`
> API 경유(S-READ k6) 읽기 천장은 AWS 본런에서 — 이 실험은 쿼리 레벨 비용의 로컬 확정.

## 1. 조건

- Stage 1a = 저장 없이 조회 시점에 `0.1×view + 0.2×like + 0.6×sales_score` 계산 + 정렬. 1만/10만 2스케일 (`product_metrics_10k/100k`).
- 대조군 = Stage 1b 인덱스 리드 (`product_ranking_daily` 오늘판 10만 행, `idx(ranking_date, score)`).
- 쿼리 4종: Top-20 페이지 / 개별 순위(COUNT 파생) × 계산식/인덱스.
- 오염 통제: 1차 런 중 앱 부팅·시드와 겹친 idx 계열은 MySQL 유휴 확인 후 재측정(run3). `run-meta.md` 기록.

## 2. 가설

- 계산식 Top-20 은 전 행 스캔+filesort 라 데이터 량에 선형 붕괴한다.
- 인덱스 리드는 스케일과 무관하게 LIMIT 20 만 읽는다.
- 개별 순위(COUNT) 는 양쪽 다 아프다 — Stage 3 ZREVRANK 의 존재 이유.

## 3. 수치

| 쿼리 | 단건 (EXPLAIN ANALYZE) | conc32 처리량 |
|---|---|---|
| 계산식 Top-20 · 1만 | 2.15ms (전 행 스캔+정렬) | 2,822 qps |
| 계산식 Top-20 · 10만 | 20.4ms | **267 qps** |
| 계산식 개별 순위 · 10만 | 26.1ms (71,633행 필터 통과) | 279 qps |
| 인덱스 Top-20 · 10만 | **2.06ms** (인덱스 룩업 20행) | 807 qps |
| 인덱스 개별 순위 · 10만 | 39.4ms (**옵티마이저가 uk 선택** — 클러스터 룩업 10만회) | **31 qps** |
| 〃 + FORCE INDEX(date,score) | 10.6ms (커버링 range 5만 엔트리) | 521 qps |

## 4. 판독

- **계산식 Top-20 은 10× 데이터에 처리량 정확히 1/10** (2,822→267). 선형 붕괴 가설 그대로 — 상품 수가 랭킹 조회 비용의 분모.
- **인덱스 Top-20 단건 2ms 는 스케일 무관** — 쓰기 시점 계산(Stage 1b)의 핵심 이득. conc32 807qps 는 로컬 docker CPU 천장이 낀 값이라 절대치는 AWS 에서.
- **개별 순위는 인덱스가 있어도 옵티마이저가 배신** — WRITING-LOG 2026-07-15 발견(uk 선택, FORCE INDEX 3배) 이 slap 처리량에서도 재현: 31 vs 521 qps (17배). FORCE INDEX 로도 O(순위) 스캔이라 순위가 낮을수록 비쌈 — ZREVRANK O(log N) 와의 구조 차이.
- 하드웨어가 좋아지면 완화되는 문제가 아니라 접근 방식의 문제 — Stage 전환의 근거로 사용.

## 5. 결정

- Stage 1a 는 예정대로 탈락 근거 확보용으로 종료. Stage 1b 읽기 경로는 페이지 조회에 한해 유효.
- 개별 순위는 Stage 1b 의 구조적 한계 데이터포인트로 보존 (FORCE INDEX 미도입 결정 유지 — 2026-07-15 로그).
- AWS 본런(EXP-02 API 경유)에서는 S-READ 램프로 p95·천장 도착률을 추가 확보.
