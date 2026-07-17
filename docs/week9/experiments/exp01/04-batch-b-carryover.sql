-- 모델 B: 사전 시딩 carry-over — 내일 날짜 행을 새로 INSERT (오늘 판은 건드리지 않음)
-- ZUNIONSTORE WEIGHTS 0.1 의 RDB 번역. 조회(오늘 행)와 다른 행에 쓰므로 경합 가설: 영향 미미
INSERT INTO ranking_exp.product_ranking_daily (ranking_date, product_id, score)
SELECT ranking_date + INTERVAL 1 DAY, product_id, ROUND(score * 0.1, 3)
FROM ranking_exp.product_ranking_daily
WHERE ranking_date = CURDATE();
