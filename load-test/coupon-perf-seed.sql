-- 선착순 쿠폰 부하 측정용 시드. SUT 부팅(ddl-auto=create) 후 수동 실행:
--   mysql -h127.0.0.1 -uapplication -papplication loopers < load-test/coupon-perf-seed.sql
--
-- 설계 근거:
-- * 계정 1만 개 — userId 중복 0 불변식을 실측하려면 발급 한도(100)보다 훨씬 많은 distinct 유저가
--   필요하다. 유저가 적으면 21번째 요청부터 ALREADY_ISSUED 로만 떨어져 SOLD_OUT 경합을 못 만든다.
-- * 비밀번호는 전부 Perf@Loop2026, BCrypt cost 4 해시 — 요청당 인증 비용을 ~1ms 로 억제
--   (cost 10 이면 측정이 BCrypt 에 지배됨. 인증세는 모든 요청에 균등 가산되는 '상수 세금'이라
--    변형 간 상대 비교를 흔들지 않는다. WRITING-LOG 결정 9-보강).
-- * identifier = 이메일 로컬파트(cpuser00001 ~ cpuser10000). k6 는 loginId 를 동일 규칙으로 생성.

SET SESSION cte_max_recursion_depth = 20000;

-- 계정 1만 (cpuser00001 ~ cpuser10000)
INSERT INTO account (name, birth_date, email, role, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT CONCAT('쿠폰유저', n),
       '1996-01-01',
       CONCAT('cpuser', LPAD(n, 5, '0'), '@loopers.com'),
       'USER',
       NOW(), NOW()
FROM seq;

INSERT INTO account_credential (account_id, method, identifier, secret, created_at, updated_at)
SELECT a.id, 'PASSWORD', SUBSTRING_INDEX(a.email, '@', 1),
       '$2y$04$KQipY1WpfceFaucJXy4ffeJ1ymPLAyKUuteE4nNlyv2R7O2XVYuNq', NOW(), NOW()
FROM account a WHERE a.email LIKE 'cpuser%@loopers.com';

-- 쿠폰 2종 (명시 id — k6 가 couponId 로 직접 지정)
--   90001: 스파이크용 한도 100 — 10초 내 1만 요청 → 정확히 100 발급 + 9900 SOLD_OUT (매진 경합)
--   90002: 지속 경합용 한도 10만 — 계단 부하로 한 hot row 에 지속 락 경합 (매진 없이 throughput/latency)
INSERT INTO coupon (id, type, name, discount_value, min_order_amount, expired_at, created_by,
                    total_quantity, issued_quantity, created_at, updated_at)
VALUES
    (90001, 'FIXED', '선착순-스파이크-100', 1000, 0, '2030-12-31 23:59:59', 1, 100, 0, NOW(), NOW()),
    (90002, 'FIXED', '선착순-지속-100000', 1000, 0, '2030-12-31 23:59:59', 1, 100000, 0, NOW(), NOW());
