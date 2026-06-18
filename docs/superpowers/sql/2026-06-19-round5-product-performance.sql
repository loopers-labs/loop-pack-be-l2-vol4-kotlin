-- Round5 상품 조회 성능 측정용 MySQL 8 스크립트
-- 실행 전 local profile 앱을 한 번 띄워 Hibernate ddl-auto=create 로 스키마를 생성한다.

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE likes;
TRUNCATE TABLE product_stocks;
TRUNCATE TABLE products;
TRUNCATE TABLE brands;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO brands (name, description, created_at, updated_at, deleted_at)
SELECT
    CONCAT('Round5 Brand ', n),
    CONCAT('Round5 benchmark brand ', n),
    NOW(6),
    NOW(6),
    NULL
FROM (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) brand_seq;

CREATE TEMPORARY TABLE round5_numbers (n INT PRIMARY KEY);

INSERT INTO round5_numbers (n)
SELECT
    d1.n
    + d2.n * 10
    + d3.n * 100
    + d4.n * 1000
    + d5.n * 10000
    + 1 AS n
FROM (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) d1
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) d2
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) d3
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) d4
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) d5;

INSERT INTO products (brand_id, name, description, price, like_count, created_at, updated_at, deleted_at)
SELECT
    ((n - 1) % 20) + 1 AS brand_id,
    CONCAT('Round5 Product ', n) AS name,
    CONCAT('Benchmark product ', n, ' / bucket ', n % 97) AS description,
    CAST(1000 + ((n * 37) % 500000) AS DECIMAL(10, 2)) AS price,
    (n * 17) % 10000 AS like_count,
    NOW(6),
    NOW(6),
    CASE WHEN n % 25 = 0 THEN NOW(6) ELSE NULL END AS deleted_at
FROM round5_numbers
WHERE n <= 100000;

INSERT INTO product_stocks (product_id, quantity, created_at, updated_at, deleted_at)
SELECT
    n AS product_id,
    (n * 13) % 500 AS quantity,
    NOW(6),
    NOW(6),
    NULL
FROM round5_numbers
WHERE n <= 100000;

DROP TEMPORARY TABLE round5_numbers;

ANALYZE TABLE products;

-- AS-IS 비교용: 최종 인덱스가 이미 생성된 상태라면 아래 DROP 을 적용한 뒤 측정한다.
-- DROP INDEX idx_products_brand_deleted_likes_id ON products;
-- DROP INDEX idx_products_deleted_likes_id ON products;

EXPLAIN ANALYZE
SELECT id, brand_id, name, price, like_count
FROM products
WHERE brand_id = 1
  AND deleted_at IS NULL
ORDER BY like_count DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT id, brand_id, name, price, like_count
FROM products
WHERE deleted_at IS NULL
ORDER BY like_count DESC, id DESC
LIMIT 20 OFFSET 0;

-- 후보 1: deleted_at 미포함 브랜드 필터 인덱스
-- CREATE INDEX idx_products_brand_likes_id ON products (brand_id, like_count, id);
-- ANALYZE TABLE products;
-- EXPLAIN ANALYZE
-- SELECT id, brand_id, name, price, like_count
-- FROM products
-- WHERE brand_id = 1
--   AND deleted_at IS NULL
-- ORDER BY like_count DESC, id DESC
-- LIMIT 20 OFFSET 0;
-- DROP INDEX idx_products_brand_likes_id ON products;

-- 최종 인덱스
-- CREATE INDEX idx_products_brand_deleted_likes_id ON products (brand_id, deleted_at, like_count, id);
-- CREATE INDEX idx_products_deleted_likes_id ON products (deleted_at, like_count, id);
-- ANALYZE TABLE products;

EXPLAIN ANALYZE
SELECT id, brand_id, name, price, like_count
FROM products FORCE INDEX (idx_products_brand_deleted_likes_id)
WHERE brand_id = 1
  AND deleted_at IS NULL
ORDER BY like_count DESC, id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT id, brand_id, name, price, like_count
FROM products FORCE INDEX (idx_products_deleted_likes_id)
WHERE deleted_at IS NULL
ORDER BY like_count DESC, id DESC
LIMIT 20 OFFSET 0;
