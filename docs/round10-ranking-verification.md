# Round 10 Ranking Verification

## Large Fixture

`seed-product-metric-daily-30m.sql` creates 30 days of `product_metric_daily` rows for 1,000,000 products.

```shell
mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers < seed-product-metric-daily-30m.sql
```

The script also includes `EXPLAIN ANALYZE` for the weekly and monthly aggregation reader query shape:

```sql
SELECT
    product_id,
    SUM(view_count) AS view_count,
    SUM(like_count) AS like_count,
    SUM(sales_amount) AS sales_amount
FROM product_metric_daily
WHERE metric_date >= @source_start
  AND metric_date < @source_end_exclusive
GROUP BY product_id
ORDER BY product_id;
```

## Query Plan Checks

Run these against a database with the schema and fixture loaded.

```sql
EXPLAIN ANALYZE
SELECT
    product_id,
    SUM(view_count) AS view_count,
    SUM(like_count) AS like_count,
    SUM(sales_amount) AS sales_amount
FROM product_metric_daily
WHERE metric_date >= DATE('2026-07-01')
  AND metric_date < DATE('2026-07-08')
GROUP BY product_id
ORDER BY product_id;

EXPLAIN ANALYZE
SELECT product_id, ranking_score
FROM mv_product_rank_weekly
WHERE base_date = DATE('2026-08-03')
ORDER BY ranking_score DESC, product_id ASC
LIMIT 100;

EXPLAIN ANALYZE
SELECT product_id, ranking_score
FROM mv_product_rank_monthly
WHERE base_date = DATE('2026-08-01')
ORDER BY ranking_score DESC, product_id ASC
LIMIT 100;
```

Expected index usage:

- `product_metric_daily`: `uk_product_metric_daily_date_product` or equivalent range access on `metric_date`.
- `mv_product_rank_weekly`: `idx_mv_product_rank_weekly_top`.
- `mv_product_rank_monthly`: `idx_mv_product_rank_monthly_top`.

## Batch Commands

```shell
java -jar commerce-batch.jar \
  --spring.batch.job.name=weeklyProductRankingJob \
  baseDate=2026-08-03

java -jar commerce-batch.jar \
  --spring.batch.job.name=monthlyProductRankingJob \
  baseDate=2026-08-01

java -jar commerce-batch.jar \
  --spring.batch.job.name=dailyRankingCarryOverJob \
  baseDate=2026-08-04
```

## Verification Commands

```shell
./gradlew :apps:commerce-batch:test \
  --tests com.loopers.job.productranking.WeeklyProductRankingJobE2ETest \
  --tests com.loopers.job.productranking.MonthlyProductRankingJobE2ETest \
  --tests com.loopers.job.ranking.DailyRankingCarryOverJobE2ETest

./gradlew :apps:commerce-api:test \
  --tests com.loopers.interfaces.api.ranking.RankingV1ApiE2ETest \
  --tests com.loopers.application.ranking.RankingCarryOverSchedulerContextTest

./gradlew :apps:commerce-batch:ktlintCheck :apps:commerce-api:ktlintCheck :modules:redis:ktlintCheck
```
