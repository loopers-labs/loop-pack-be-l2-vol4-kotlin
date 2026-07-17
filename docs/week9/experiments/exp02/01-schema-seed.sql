-- EXP-02 (Stage 1a): 조회 시점 계산 — product_metrics 형태 테이블 (누적, 날짜 차원 없음)
-- score 컬럼이 없고, 조회 쿼리 안에서 0.1·view + 0.2·like + 0.6·sales 를 합성한다.
CREATE DATABASE IF NOT EXISTS ranking_exp;

DROP TABLE IF EXISTS ranking_exp.product_metrics_10k;
DROP TABLE IF EXISTS ranking_exp.product_metrics_100k;

CREATE TABLE ranking_exp.product_metrics_10k (
    product_id  BIGINT NOT NULL PRIMARY KEY,
    view_count  BIGINT NOT NULL DEFAULT 0,
    like_count  BIGINT NOT NULL DEFAULT 0,
    sales_score DOUBLE NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ranking_exp.product_metrics_100k LIKE ranking_exp.product_metrics_10k;

SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO ranking_exp.product_metrics_10k (product_id, view_count, like_count, sales_score)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 10000)
SELECT n, FLOOR(RAND() * 10000), FLOOR(RAND() * 2000), ROUND(RAND() * 100000, 1)
FROM seq;

INSERT INTO ranking_exp.product_metrics_100k (product_id, view_count, like_count, sales_score)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
SELECT n, FLOOR(RAND() * 10000), FLOOR(RAND() * 2000), ROUND(RAND() * 100000, 1)
FROM seq;
