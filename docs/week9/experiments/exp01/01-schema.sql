-- EXP-01: 자정 배치 × 조회 경합 실측 — 전용 스키마 (loopers 오염 방지)
CREATE DATABASE IF NOT EXISTS ranking_exp;

CREATE TABLE IF NOT EXISTS ranking_exp.product_ranking_daily (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ranking_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_prd_date_product (ranking_date, product_id),
    KEY idx_prd_date_score (ranking_date, score DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
