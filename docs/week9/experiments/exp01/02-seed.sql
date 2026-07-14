-- EXP-01 시드: 오늘 날짜 판에 상품 10만 개 · 랜덤 점수
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO ranking_exp.product_ranking_daily (ranking_date, product_id, score)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT CURDATE(), n, ROUND(RAND() * 1000, 1)
FROM seq;
