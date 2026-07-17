-- perf-seed-ranking.sql · Stage 1b 랭킹판 시드 — 멱법칙 분포 (2026-07-16)
-- ----------------------------------------------------------------------------
-- EXP-02 로컬 파일럿의 한계 반영: 균등분포(RAND()×1000)는 실제와 다르고,
--   개별 순위 쿼리 비용이 O(순위)라 score 분포가 측정 결과를 좌우한다.
-- → score 를 perf-seed-l5 의 like_count(무신사 멱법칙 기반)에 비례시켜
--   실분포와 정렬시키고, 인기 상품(상세 조회가 몰리는)일수록 순위 쿼리가 싸지는
--   실제 트래픽 특성이 측정에 그대로 실리게 한다.
-- score = like_count × 0.2 (좋아요 가중치) + CRC32 노이즈(0~9.9, 동점 분산)
-- 내일판 = 오늘판 × 0.1 (dual write carry 상태 재현 — 경계 조회 ?date=내일 대비)
-- 선행: perf-seed-l5.sql (product 적재), 앱 DDL 생성 완료
-- 실행: sudo docker exec -i mysql mysql -uapplication -papplication loopers < perf-seed-ranking.sql
-- 1만 스케일 런은 아래 INSERT 의 product 에 WHERE id <= 10000 을 붙여 재시드.

DELETE FROM product_ranking_daily;

INSERT INTO product_ranking_daily (ranking_date, product_id, score, created_at, updated_at)
SELECT CURDATE(), id,
       ROUND(like_count * 0.2 + (CRC32(id) % 100) / 10, 4),
       NOW(6), NOW(6)
FROM product;

INSERT INTO product_ranking_daily (ranking_date, product_id, score, created_at, updated_at)
SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY), product_id,
       ROUND(score * 0.1, 4),
       NOW(6), NOW(6)
FROM product_ranking_daily
WHERE ranking_date = CURDATE();

SELECT ranking_date, COUNT(*) AS rows_cnt, ROUND(SUM(score), 2) AS score_sum,
       ROUND(MAX(score), 2) AS max_score
FROM product_ranking_daily GROUP BY ranking_date;
