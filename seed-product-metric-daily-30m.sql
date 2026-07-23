-- product_metric_daily 30,000,000-row seed script for MySQL 8.x.
--
-- Assumption:
-- - 1,000,000 products
-- - 30 metric dates
-- - 1 row per (metric_date, product_id)
--
-- Run example:
--   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers < seed-product-metric-daily-30m.sql
--
-- Tune these two values before running if needed.
SET @metric_start_date = DATE('2026-07-01');
SET @metric_day_count = 30;

SET autocommit = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_product_ids;
DROP TEMPORARY TABLE IF EXISTS tmp_digits;
DROP TABLE IF EXISTS seed_digits_30m;

CREATE TABLE IF NOT EXISTS product_metric_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_metric_daily_date_product (metric_date, product_id),
    KEY idx_product_metric_daily_product_date (product_id, metric_date)
);

-- MySQL cannot reopen the same TEMPORARY table multiple times in one query.
-- Keep the 10-row digit helper as a regular table and drop it at the end.
CREATE TABLE seed_digits_30m (
    d TINYINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO seed_digits_30m (d)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TEMPORARY TABLE tmp_product_ids (
    product_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO tmp_product_ids (product_id)
SELECT
    ones.d
    + tens.d * 10
    + hundreds.d * 100
    + thousands.d * 1000
    + ten_thousands.d * 10000
    + hundred_thousands.d * 100000
    + 1 AS product_id
FROM seed_digits_30m AS ones
CROSS JOIN seed_digits_30m AS tens
CROSS JOIN seed_digits_30m AS hundreds
CROSS JOIN seed_digits_30m AS thousands
CROSS JOIN seed_digits_30m AS ten_thousands
CROSS JOIN seed_digits_30m AS hundred_thousands
ORDER BY product_id;

DROP TABLE IF EXISTS seed_digits_30m;

DROP PROCEDURE IF EXISTS seed_product_metric_daily_30m;

DELIMITER $$

CREATE PROCEDURE seed_product_metric_daily_30m()
BEGIN
    DECLARE day_offset INT DEFAULT 0;
    DECLARE metric_date_value DATE;

    WHILE day_offset < @metric_day_count DO
        SET metric_date_value = DATE_ADD(@metric_start_date, INTERVAL day_offset DAY);

        INSERT INTO product_metric_daily (
            metric_date,
            product_id,
            view_count,
            like_count,
            sales_amount,
            created_at,
            updated_at
        )
        SELECT
            metric_date_value AS metric_date,
            product_id,
            MOD(product_id + day_offset, 11) AS view_count,
            MOD(product_id * 3 + day_offset, 7) AS like_count,
            MOD(product_id * 97 + day_offset * 13, 100000) AS sales_amount,
            CURRENT_TIMESTAMP(6) AS created_at,
            CURRENT_TIMESTAMP(6) AS updated_at
        FROM tmp_product_ids
        ON DUPLICATE KEY UPDATE
            view_count = VALUES(view_count),
            like_count = VALUES(like_count),
            sales_amount = VALUES(sales_amount),
            updated_at = CURRENT_TIMESTAMP(6);

        SELECT CONCAT('seeded metric_date=', metric_date_value, ', rows=', ROW_COUNT()) AS progress;
        SET day_offset = day_offset + 1;
    END WHILE;
END$$

DELIMITER ;

CALL seed_product_metric_daily_30m();

DROP PROCEDURE IF EXISTS seed_product_metric_daily_30m;
DROP TEMPORARY TABLE IF EXISTS tmp_product_ids;

SELECT
    COUNT(*) AS total_rows,
    COUNT(DISTINCT product_id) AS product_count,
    MIN(metric_date) AS min_metric_date,
    MAX(metric_date) AS max_metric_date
FROM product_metric_daily
WHERE metric_date >= @metric_start_date
  AND metric_date < DATE_ADD(@metric_start_date, INTERVAL @metric_day_count DAY);

-- Query shape used by weekly/monthly aggregation readers.
-- Change these dates to match the batch source range you want to inspect.
SET @source_start = @metric_start_date;
SET @source_end_exclusive = DATE_ADD(@metric_start_date, INTERVAL 7 DAY);

EXPLAIN ANALYZE
SELECT
    product_id,
    SUM(view_count) AS view_count,
    SUM(like_count) AS like_count,
    SUM(sales_amount) AS sales_amount
FROM product_metric_daily
WHERE metric_date >= @source_start
  AND metric_date < @source_end_exclusive
GROUP BY product_id
ORDER BY product_id;

SET @source_start = @metric_start_date;
SET @source_end_exclusive = DATE_ADD(@metric_start_date, INTERVAL @metric_day_count DAY);

EXPLAIN ANALYZE
SELECT
    product_id,
    SUM(view_count) AS view_count,
    SUM(like_count) AS like_count,
    SUM(sales_amount) AS sales_amount
FROM product_metric_daily
WHERE metric_date >= @source_start
  AND metric_date < @source_end_exclusive
GROUP BY product_id
ORDER BY product_id;
