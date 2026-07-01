-- =====================================================================
-- K6 부하테스트용 시드 데이터
-- ---------------------------------------------------------------------
-- 실행:
--   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers < k6/seed.sql
--
-- 핵심 포인트:
--   - 앱의 비밀번호 인코딩(EncryptionUtil)은 SHA-256 hex(salt 없음)이라
--     MySQL SHA2(pw, 256) 과 정확히 일치한다 → SQL 로 심은 유저도 로그인 검증 통과.
--   - 부하 중 재고 부족 노이즈를 없애기 위해 재고를 매우 크게 잡는다.
--   - 마지막 SELECT 가 출력하는 brand_id 를 K6 의 -e BRAND_ID 로 넘긴다.
-- =====================================================================

-- 생성 규모 (필요시 조정). 재귀 CTE 깊이라 cte_max_recursion_depth(기본 1000) 이하 권장.
SET @USER_COUNT     = 200;
SET @PRODUCT_COUNT  = 50;
SET @STOCK_PER_ITEM = 100000000;
SET @PASSWORD       = 'password1234';

-- ---------------------------------------------------------------------
-- 재실행 안전: 기존 부하테스트 데이터 정리 (자식 → 부모 순서)
-- ---------------------------------------------------------------------
DELETE FROM auth  WHERE login_id LIKE 'loadtest%';
DELETE FROM stock WHERE product_id IN (SELECT id FROM product WHERE name LIKE 'loadtest-product-%');
DELETE FROM product WHERE name LIKE 'loadtest-product-%';
DELETE FROM brand   WHERE name = 'loadtest-brand';
DELETE FROM user    WHERE email LIKE 'loadtest%@example.com';

-- ---------------------------------------------------------------------
-- 1) 유저: loadtest-user-0001 .. (이메일/로그인ID 에 번호 매핑)
-- ---------------------------------------------------------------------
INSERT INTO user (name, birth, email, created_at, updated_at)
SELECT CONCAT('loadtest-user-', LPAD(n, 4, '0')),
       '1990-01-01',
       CONCAT('loadtest', LPAD(n, 4, '0'), '@example.com'),
       NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < @USER_COUNT
    )
    SELECT n FROM seq
) AS s;

-- 2) 인증: login_id = 이메일 prefix (loadtest0001 ..), 비번은 SHA-256
INSERT INTO auth (user_id, login_id, password, created_at, updated_at)
SELECT id,
       SUBSTRING_INDEX(email, '@', 1),   -- 'loadtest0001'
       SHA2(@PASSWORD, 256),
       NOW(), NOW()
FROM user
WHERE email LIKE 'loadtest%@example.com';

-- ---------------------------------------------------------------------
-- 3) 브랜드 1개 + 상품 N개 + 상품별 재고
-- ---------------------------------------------------------------------
INSERT INTO brand (name, description, created_at, updated_at)
VALUES ('loadtest-brand', 'k6 load test brand', NOW(), NOW());
SET @brand_id = LAST_INSERT_ID();

INSERT INTO product (name, price, description, brand_id, created_at, updated_at)
SELECT CONCAT('loadtest-product-', LPAD(n, 4, '0')),
       1000,                              -- 단가 (결제 금액 작게)
       'k6 load test product',
       @brand_id,
       NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < @PRODUCT_COUNT
    )
    SELECT n FROM seq
) AS s;

INSERT INTO stock (product_id, quantity, created_at, updated_at)
SELECT id, @STOCK_PER_ITEM, NOW(), NOW()
FROM product
WHERE brand_id = @brand_id;

-- ---------------------------------------------------------------------
-- 결과 출력: 이 brand_id 를 K6 -e BRAND_ID 로 넘긴다.
-- ---------------------------------------------------------------------
SELECT @brand_id                                                           AS brand_id,
       (SELECT COUNT(*) FROM auth    WHERE login_id LIKE 'loadtest%')      AS seeded_users,
       (SELECT COUNT(*) FROM product WHERE brand_id = @brand_id)           AS seeded_products,
       @USER_COUNT                                                         AS user_count_for_k6;
