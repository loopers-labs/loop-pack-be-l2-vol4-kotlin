-- 캐시 실습용 추가 시드: user 1만, likes 100만
-- likes 는 (user_id, product_id) 유니크 제약이 있어 중복 없는 쌍을 결정적으로 생성한다.
--   user_id   = ((n-1) % 10000) + 1            (1..10000)
--   group     = (n-1) DIV 10000                (0..99, 유저당 100개)
--   product_id= ((user_id-1)*20 + group) % 200000 + 1   (유저 내 100개 모두 distinct)

SET SESSION cte_max_recursion_depth = 1000000;

-- ---------- DDL (LikeEntity 매핑에 맞춤) ----------
CREATE TABLE IF NOT EXISTS likes (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_likes_user_id_product_id (user_id, product_id),
  KEY idx_likes_product_id_user_id (product_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 재실행 안전 ----------
TRUNCATE TABLE likes;
TRUNCATE TABLE user;

SET autocommit = 0;
SET unique_checks = 0;

-- 1) user 10,000 (id 1..10000)
--    name  : 성 + 이름 두 글자 조합으로 실제 한국 이름처럼 생성 (중복 허용)
--    email : 유니크 제약이 있어 userN@test.com 으로 결정적으로 생성
INSERT INTO user (id, created_at, updated_at, deleted_at, name, birth, email)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
  n,
  NOW(6) - INTERVAL FLOOR(RAND() * 525600) MINUTE,
  NOW(6),
  NULL,
  CONCAT(
    ELT((n % 18) + 1, '김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안','송'),
    ELT(((n DIV 18) % 14) + 1, '민','서','지','현','준','예','수','하','유','시','도','은','재','우'),
    ELT(((n DIV 7) % 14) + 1, '준','우','연','진','호','아','윤','서','현','희','수','영','빈','율')
  ),
  DATE_SUB(CURDATE(), INTERVAL (6570 + FLOOR(RAND() * 14600)) DAY),
  CONCAT('user', n, '@test.com')
FROM seq;
COMMIT;

-- 2) likes 1,000,000 (중복 없는 user×product 쌍)
INSERT INTO likes (user_id, product_id)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000000
)
SELECT
  ((n - 1) % 10000) + 1 AS user_id,
  ((((n - 1) % 10000)) * 20 + ((n - 1) DIV 10000)) % 200000 + 1 AS product_id
FROM seq;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;
