CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    product_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    ranking_score DOUBLE NOT NULL DEFAULT 0.0,
    `rank` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, period_start),
    INDEX idx_weekly_period_rank (period_start, `rank`)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    product_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    ranking_score DOUBLE NOT NULL DEFAULT 0.0,
    `rank` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, period_start),
    INDEX idx_monthly_period_rank (period_start, `rank`)
);
