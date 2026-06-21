-- likes 100만을 인기 편중(롱테일) 분포로 재생성 + like_count 재집계
-- product_id = FLOOR(POW((n-1)/1e6, 2) * 199999) + 1
--   - n에 단조증가 → 각 product 의 n-구간이 연속
--   - 구간 내 user_id = ((n-1)%10000)+1 는 모두 distinct (최대 인기 상품도 ~2,200건 < 1만 유저)
--   => (user_id, product_id) 유니크 제약 자동 보장, 낮은 id 일수록 인기

SET SESSION cte_max_recursion_depth = 1000000;

TRUNCATE TABLE likes;

SET autocommit = 0;
SET unique_checks = 0;

INSERT INTO likes (user_id, product_id)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000000
)
SELECT
  ((n - 1) % 10000) + 1 AS user_id,
  FLOOR(POW((n - 1) / 1000000.0, 2) * 199999) + 1 AS product_id
FROM seq;
COMMIT;

SET unique_checks = 1;
SET autocommit = 1;

-- like_count 재집계 (좋아요 0건 상품은 0 으로)
UPDATE like_count lc
SET lc.count = (SELECT COUNT(*) FROM likes l WHERE l.product_id = lc.product_id);
