-- R9 랭킹 정산(reconciliation) — 성능 런 종료 + 컨슈머 랙 0 도달 후 실행한다.
--   기대값 E = 0.1·V + 0.2·(L+ − L−) + 0.7·O  (V·L·O 는 k6 커스텀 카운터 출력에서)
--   허용 오차: like/order = 0 (outbox 원자 발행 — 불일치는 버그).
--             view = 결손만 허용(저장소 ≤ k6, @Async 직접 발행 유실 경로), 초과(중복 가산)는 어떤 경우에도 버그.
--   ⚠️ product_metrics 컬럼명은 R7 스키마 기준 — 실행 전 SHOW COLUMNS 로 대조할 것.
--   사용: mysql -h <infra> -u root -p loopers < load-test/verify-ranking.sql

-- 측정 서버는 TZ=KST 고정 (infra-compose mysql --default-time-zone=+09:00) → CURDATE() 그대로.
SET @today    = CURDATE();
SET @tomorrow = DATE_ADD(@today, INTERVAL 1 DAY);

-- [1] 오늘판 점수 합 — 기대값 E 와 대사
SELECT 'today_sum' AS metric, ROUND(SUM(score), 4) AS value, COUNT(*) AS rows_cnt
FROM product_ranking_daily WHERE ranking_date = @today;

-- [2] 내일판 점수 합 — dual write carry 검증 (기대: 오늘판 × 0.1)
SELECT 'tomorrow_sum' AS metric, ROUND(SUM(score), 4) AS value, COUNT(*) AS rows_cnt
FROM product_ranking_daily WHERE ranking_date = @tomorrow;

-- [2-1] carry 비율 — 1 회 런(모든 이벤트가 오늘 발생) 기준 0.1 에 수렴해야 함
SELECT 'carry_ratio' AS metric,
       ROUND((SELECT SUM(score) FROM product_ranking_daily WHERE ranking_date = @tomorrow)
           / (SELECT SUM(score) FROM product_ranking_daily WHERE ranking_date = @today), 6) AS value;

-- [3] R7 집계 교차 대사 — METRICS 구독 결과 (RANKING 구독과는 별도 트랜잭션·별도 멱등)
SELECT 'metrics_totals' AS metric,
       SUM(view_count) AS views, SUM(like_count) AS likes, SUM(sales_count) AS sales
FROM product_metrics;

-- [4] 소비 이벤트 총량 — 구독별 처리 수. k6 발행 성공 수와 대사 (view 유실률 = 1 − handled/발행)
SELECT 'event_handled' AS metric, subscription, COUNT(*) AS value
FROM event_handled GROUP BY subscription;

-- [5] 이상 데이터 스캔 — 오늘·내일 외 판(보존 배치 대상 제외), NULL score
SELECT 'other_dates' AS metric, ranking_date, COUNT(*) AS rows_cnt
FROM product_ranking_daily
WHERE ranking_date NOT IN (@today, @tomorrow)
GROUP BY ranking_date;

-- [6] 상위 20 스냅샷 — 카오스 런(재기동) 전후 비교·ZSET(Stage 3) 대조용으로 결과 보관
SELECT product_id, ROUND(score, 4) AS score
FROM product_ranking_daily
WHERE ranking_date = @today
ORDER BY score DESC, product_id ASC
LIMIT 20;
