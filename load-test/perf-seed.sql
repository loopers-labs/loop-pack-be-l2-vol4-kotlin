-- 부하 측정용 시드. local 부팅(ddl-auto=create) 후 수동 실행:
--   mysql -h127.0.0.1 -uapplication -papplication loopers < load-test/perf-seed.sql
-- 계정 비밀번호는 전부 Perf@Loop2026, BCrypt cost 4 해시 (요청당 인증 비용을 ~1ms로 억제 — cost 10이면 측정이 BCrypt에 지배됨)

-- 계정 20개 (perfuser1 ~ perfuser20)
INSERT INTO account (name, birth_date, email, role, created_at, updated_at)
SELECT CONCAT('부하유저', n.n), '1996-01-01', CONCAT('perfuser', n.n, '@loopers.com'), 'USER', NOW(), NOW()
FROM (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
    UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
) n;

INSERT INTO account_credential (account_id, method, identifier, secret, created_at, updated_at)
SELECT a.id, 'PASSWORD', SUBSTRING_INDEX(a.email, '@', 1),
       '$2y$04$KQipY1WpfceFaucJXy4ffeJ1ymPLAyKUuteE4nNlyv2R7O2XVYuNq', NOW(), NOW()
FROM account a WHERE a.email LIKE 'perfuser%@loopers.com';

-- 핫 상품 1개 (id 1001) + 분산 상품 10개 (id 1011~1020), 전부 10,000원
INSERT INTO product (id, brand_id, name, price, like_count, status, created_at, updated_at)
VALUES (1001, 1, '핫상품', 10000, 0, 'ACTIVE', NOW(), NOW());

INSERT INTO product (id, brand_id, name, price, like_count, status, created_at, updated_at)
SELECT 1010 + n.n, 1, CONCAT('분산상품', n.n), 10000, 0, 'ACTIVE', NOW(), NOW()
FROM (
    SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) n;

-- 재고 (소진되지 않게 1억)
INSERT INTO inventory (product_id, quantity, created_at, updated_at)
SELECT id, 100000000, NOW(), NOW() FROM product WHERE id >= 1001;
