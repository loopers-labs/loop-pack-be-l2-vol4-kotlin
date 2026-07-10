-- ============================================================================
-- perf-seed-l5.sql  ·  Week5 조회 병목 측정용 시드 (옵션 B: 비정규화 전/후 정합)
-- ----------------------------------------------------------------------------
-- 분포 설계: docs/week5/03-data-distribution.html (무신사 멱법칙 기반)
--   유저 300,000 / 브랜드 500(4티어) / 상품 100,750 / product_like ~600만(진실의 원천)
--
-- ▶ 핵심 불변식:  like_count(비정규화 컬럼) == COUNT(product_like)  (전 상품 일치)
--   → 비정규화 "전"(방식 A: 실시간 COUNT/JOIN) vs "후"(방식 B: 컬럼)를 같은 결과로 비교 가능.
--   상품마다 정확히 like_count 개의 product_like 행을 생성해 일치를 구조적으로 보장한다.
--
-- 실행 (local 부팅 = ddl-auto=create 로 테이블 생성된 뒤):
--   mysql -h127.0.0.1 -uapplication -papplication loopers < load-test/perf-seed-l5.sql
--   ※ product_like ~600만 행이라 적재에 1~3분 소요될 수 있음.
--
-- 재현성: RAND() 대신 CRC32(seq) 결정적 해시 → 매 실행 동일 데이터.
-- ============================================================================

-- 멱등 재적재: 측정 데이터 비우고 다시 (id/범위 한정).
-- FK는 account_credential.account_id -> account.id 하나뿐 → 자식(credential) 먼저 삭제.
DELETE FROM product_like;
DELETE FROM product WHERE brand_id BETWEEN 1 AND 500;
DELETE FROM brand   WHERE id BETWEEN 1 AND 500;
DELETE FROM account_credential WHERE account_id BETWEEN 1 AND 300000;  -- account FK 자식 먼저
DELETE FROM account WHERE id BETWEEN 1 AND 300000;

-- ----------------------------------------------------------------------------
-- 1) 브랜드 500 (id 1..500 명시 — 상품 티어 매핑이 이 id에 의존)
-- ----------------------------------------------------------------------------
INSERT INTO brand (id, name, description, status, created_at, updated_at)
SELECT seq + 1, CONCAT('brand-', seq + 1), NULL, 'ACTIVE', NOW(6), NOW(6)
FROM (
    SELECT d0.n + d1.n*10 + d2.n*100 AS seq
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
) g
WHERE seq < 500;

-- ----------------------------------------------------------------------------
-- 2) 유저(account) 300,000 (id 1..300000 명시 — product_like.user_id 가 참조)
--    email UNIQUE(uk_account_email)
-- ----------------------------------------------------------------------------
INSERT INTO account (id, name, birth_date, email, role, created_at, updated_at)
SELECT seq + 1, CONCAT('부하유저', seq + 1), '1996-01-01',
       CONCAT('perf', seq + 1, '@loopers.com'), 'USER', NOW(6), NOW(6)
FROM (
    SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) g
WHERE seq < 300000;

-- ----------------------------------------------------------------------------
-- 3) 상품 100,750 — like_count 를 "목표 좋아요 수"로 박는다 (4)에서 그만큼 행 생성)
--    brand_id: 티어 매핑 / price: 로그분포 / like_count: 롱테일
-- ----------------------------------------------------------------------------
INSERT INTO product (brand_id, name, price, like_count, status, created_at, updated_at)
SELECT
    CASE
        WHEN seq <  40000 THEN 1   + FLOOR(seq            / 8000)
        WHEN seq <  70000 THEN 6   + FLOOR((seq - 40000)  / 1500)
        WHEN seq <  88750 THEN 26  + FLOOR((seq - 70000)  / 250)
        ELSE                        101 + FLOOR((seq - 88750) / 30)
    END                                                       AS brand_id,
    CONCAT('product-', seq)                                   AS name,
    FLOOR(POW(10, 3 + (CRC32(CONCAT(seq, '-price')) % 1000) / 1000.0 * 3.3)) AS price,
    -- like_count 롱테일 (= 생성할 product_like 행 수). 최대 ~6만 (유저 30만 내 distinct 가능).
    CASE
        WHEN seq % 100 = 0 THEN                                   -- 핫 ~1%
            CASE WHEN CRC32(seq) % 50 = 0
                 THEN 30000 + (CRC32(seq) % 30001)                -- 메가히트 ~2%: 30,000 ~ 60,000
                 ELSE 1000  + (CRC32(seq) % 4000) END             -- 나머지 핫: 1,000 ~ 5,000
        WHEN seq % 100 BETWEEN 1 AND 9 THEN 50 + (CRC32(seq) % 201) -- 중간 ~9%: 50 ~ 250
        ELSE CRC32(seq) % 21                                       -- 롱테일 ~90%: 0 ~ 20
    END                                                       AS like_count,
    'ACTIVE', NOW(6), NOW(6)
FROM (
    SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000 + d5.n*100000 AS seq
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) g
WHERE seq < 100750;

-- ----------------------------------------------------------------------------
-- 4) product_like ~600만 — 상품마다 정확히 like_count 개. (= 비정규화 "전" 진실)
--    인덱스 있는 임시 nums(0..60000) 와 JOIN n.k < like_count → 상품당 like_count 행.
--    user_id = ((CRC32(productId) + k) % 300000) + 1  → 같은 상품 내 distinct(UNIQUE 충족).
-- ----------------------------------------------------------------------------
CREATE TEMPORARY TABLE nums (k INT PRIMARY KEY);
INSERT INTO nums (k)
SELECT d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
WHERE (d0.n + d1.n*10 + d2.n*100 + d3.n*1000 + d4.n*10000) <= 60000;

INSERT INTO product_like (user_id, product_id, created_at, updated_at)
SELECT ((CRC32(p.id) + n.k) % 300000) + 1, p.id, NOW(6), NOW(6)
FROM product p
JOIN nums n ON n.k < p.like_count
WHERE p.brand_id BETWEEN 1 AND 500;

DROP TEMPORARY TABLE nums;

-- ============================================================================
-- 검증
-- ============================================================================
SELECT (SELECT COUNT(*) FROM brand)        AS brands,
       (SELECT COUNT(*) FROM account)      AS users,
       (SELECT COUNT(*) FROM product)      AS products,
       (SELECT COUNT(*) FROM product_like) AS likes;

-- ▶ 핵심: 비정규화 전/후 일치 검증 — mismatch 가 0 이어야 한다.
SELECT COUNT(*) AS mismatch
FROM product p
LEFT JOIN (SELECT product_id, COUNT(*) c FROM product_like GROUP BY product_id) pl
       ON pl.product_id = p.id
WHERE p.like_count <> COALESCE(pl.c, 0);

-- like_count 버킷 분포
SELECT CASE WHEN like_count >= 1000 THEN '1.핫(>=1k)'
            WHEN like_count >= 50   THEN '2.중간(50~1k)'
            ELSE                         '3.롱테일(0~50)' END AS bucket,
       COUNT(*) AS products,
       ROUND(COUNT(*) / (SELECT COUNT(*) FROM product) * 100, 1) AS pct
FROM product GROUP BY bucket ORDER BY bucket;

-- 티어별 상품 분포
SELECT CASE WHEN brand_id <= 5 THEN '1.메가' WHEN brand_id <= 25 THEN '2.대형'
            WHEN brand_id <= 100 THEN '3.중형' ELSE '4.소형' END AS tier,
       COUNT(DISTINCT brand_id) AS brands, COUNT(*) AS products
FROM product GROUP BY tier ORDER BY tier;
