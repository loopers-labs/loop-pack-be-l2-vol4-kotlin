-- admin 계정 1개 seed
--   loginId = loopersadmin  (점 미허용 CredentialIdentifier regex `^[A-Za-z0-9]+$` 통과 위함)
--   password = Admin@Loop2026 (BCrypt 해시 박음, sanity는 BCryptSeedHashVerificationTest 참조)
-- 주의: X-Loopers-Ldap 헤더 값은 명세 그대로 "loopers.admin" — LoginId와 별개 식별자.
-- local profile에서만 실행 (test profile은 spring.sql.init.mode=never로 차단).
-- ddl-auto=create이므로 매 부팅 시 drop+create 후 본 INSERT 재실행 — idempotent 보장 불필요.

INSERT INTO account (name, birth_date, email, role, created_at, updated_at)
VALUES ('관리자', '1990-01-01', 'admin@loopers.com', 'ADMIN', NOW(), NOW());

INSERT INTO account_credential (account_id, method, identifier, secret, created_at, updated_at)
SELECT id, 'PASSWORD', 'loopersadmin', '$2a$10$Sv5ZNc9oOeC9mLGu2ftbuO5A8UKM3q2JJPa/R.xwVx1fs67FAPHv2', NOW(), NOW()
FROM account WHERE email = 'admin@loopers.com';
