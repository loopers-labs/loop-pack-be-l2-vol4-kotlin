-- perf-seed-queue-users.sql · 대기열 여정(queue-order-flow.js)용 perfN 자격증명 부여
-- ----------------------------------------------------------------------------
-- queue-order-flow.js 는 여정마다 고유 계정(perf1 ~ perf{USER_POOL, 기본 20000})이 필요하다
--   (대기열이 userId 기반 ZADD NX·토큰 키라 계정 공유 시 줄·토큰이 겹쳐 여정 오염).
-- perf-seed-l5.sql 이 만든 account(email perfN@loopers.com)에는 자격증명이 없어 여기서 부여한다.
--   R8 측정 때 infra 노드에서 즉석 실행하고 자산화하지 않았던 것을 레포로 승격 (2026-07-16).
-- 비밀번호는 전부 Perf@Loop2026, BCrypt cost 4 (요청당 인증 비용 ~1ms 억제).
-- 선행: perf-seed-l5.sql (account 1..300000 생성)
-- 실행: docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < load-test/perf-seed-queue-users.sql

DELETE ac FROM account_credential ac
JOIN account a ON a.id = ac.account_id
WHERE a.email LIKE 'perf%@loopers.com' AND a.email NOT LIKE 'perfuser%';

INSERT INTO account_credential (account_id, method, identifier, secret, created_at, updated_at)
SELECT a.id, 'PASSWORD', SUBSTRING_INDEX(a.email, '@', 1),
       '$2y$04$KQipY1WpfceFaucJXy4ffeJ1ymPLAyKUuteE4nNlyv2R7O2XVYuNq', NOW(6), NOW(6)
FROM account a
WHERE a.email LIKE 'perf%@loopers.com' AND a.email NOT LIKE 'perfuser%'
  AND CAST(SUBSTRING(SUBSTRING_INDEX(a.email, '@', 1), 5) AS UNSIGNED) BETWEEN 1 AND 20000;

SELECT COUNT(*) AS queue_user_credentials
FROM account_credential WHERE identifier REGEXP '^perf[0-9]+$';
