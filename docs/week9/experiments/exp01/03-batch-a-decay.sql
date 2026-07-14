-- 모델 A: 제자리 전면 감쇠 — 오늘 판 10만 행을 통째로 UPDATE (날짜 컬럼 없는 설계의 자정 배치를 재현)
-- 주의: 재실행하면 ×0.01 (비멱등)
UPDATE ranking_exp.product_ranking_daily
SET score = score * 0.1
WHERE ranking_date = CURDATE();
