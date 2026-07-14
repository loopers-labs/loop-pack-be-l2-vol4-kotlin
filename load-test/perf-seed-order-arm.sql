-- ============================================================================
-- perf-seed-order-arm.sql · M1 주문 암 전용 시드 (EXP-04)
-- ----------------------------------------------------------------------------
-- 반드시 perf-seed-l5.sql(탐색 배경 데이터) "이후"에 실행한다.
--   mysql -h127.0.0.1 -uapplication -papplication loopers < load-test/perf-seed-order-arm.sql
--
-- id 충돌 회피 설계:
--   · 계정: 명시 id 없이 INSERT → l5의 1..300000 다음(300001~)으로 자동 배정.
--     email 도 perfuserN@ (l5는 perfN@) 로 구분.
--   · 상품: 900001(핫) / 900011~900020(분산) — l5 auto id(~100,750)와 충돌 없음.
--     price 10000 고정 (k6 주문 본문의 expectedOriginalAmount 와 일치해야 함).
--     like_count 0 → l5 product_like 생성 로직과도 무간섭 (이 시드가 나중이라 원래 무간섭).
-- 계정 비밀번호는 전부 Perf@Loop2026, BCrypt cost 4 (요청당 인증 비용 ~1ms 억제)
-- ============================================================================

-- 멱등 재적재
DELETE FROM inventory WHERE product_id BETWEEN 900001 AND 900020;
DELETE FROM product WHERE id BETWEEN 900001 AND 900020;
DELETE FROM account_credential WHERE identifier LIKE 'perfuser%';
DELETE FROM account WHERE email LIKE 'perfuser%@loopers.com';

-- 주문용 계정 20개 (perfuser1 ~ perfuser20)
INSERT INTO account (name, birth_date, email, role, created_at, updated_at)
SELECT CONCAT('부하주문유저', n.n), '1996-01-01', CONCAT('perfuser', n.n, '@loopers.com'), 'USER', NOW(6), NOW(6)
FROM (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
    UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
) n;

INSERT INTO account_credential (account_id, method, identifier, secret, created_at, updated_at)
SELECT a.id, 'PASSWORD', SUBSTRING_INDEX(a.email, '@', 1),
       '$2y$04$KQipY1WpfceFaucJXy4ffeJ1ymPLAyKUuteE4nNlyv2R7O2XVYuNq', NOW(6), NOW(6)
FROM account a WHERE a.email LIKE 'perfuser%@loopers.com';

-- 핫 상품 1개(900001) + 분산 상품 10개(900011~900020), 전부 10,000원
INSERT INTO product (id, brand_id, name, price, like_count, status, created_at, updated_at)
VALUES (900001, 1, '핫상품', 10000, 0, 'ACTIVE', NOW(6), NOW(6));

INSERT INTO product (id, brand_id, name, price, like_count, status, created_at, updated_at)
SELECT 900010 + n.n, 1, CONCAT('분산상품', n.n), 10000, 0, 'ACTIVE', NOW(6), NOW(6)
FROM (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) n;

-- 재고 (소진되지 않게 1억)
INSERT INTO inventory (product_id, quantity, created_at, updated_at)
SELECT id, 100000000, NOW(6), NOW(6) FROM product WHERE id BETWEEN 900001 AND 900020;

-- 검증
SELECT (SELECT COUNT(*) FROM account WHERE email LIKE 'perfuser%@loopers.com') AS order_users,
       (SELECT COUNT(*) FROM product WHERE id BETWEEN 900001 AND 900020)       AS order_products,
       (SELECT COUNT(*) FROM inventory WHERE product_id BETWEEN 900001 AND 900020) AS inventories;
