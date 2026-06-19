# Product List Index Experiments

Date: 2026-06-19

Target:
- `GET /api/v1/products?sort=latest|price_asc|likes_desc&page=0&size=20`
- `GET /api/v1/brands/{brandId}/products?sort=latest|price_asc|likes_desc&page=0&size=20`

Benchmark command:

```bash
BASE_URL=http://localhost:18080 BRAND_ID=1001 k6 run --summary-export <summary.json> performance/k6/product-list-likes-desc.js
```

Primary success metric: every custom latency trend should decrease: `min`, `avg`, `med`, `p(90)`, `p(95)`, `p(99)`, `max`.

Guardrails: `http_req_failed`, `product_list_errors`, `checks`, `dropped_iterations`, and request rate.

## Experiment 1: Broad Index Set On Full Join Query

Hypothesis: adding query-specific indexes to the full join product list query would reduce latency.

Query shape: one JPQL query joining `products`, `brands`, `product_stats`, and `product_stocks`.

Index condition:
- Before: target product/product_stats/product_stock indexes removed for the comparison.
- After: runtime DB had `product_stats` likes index plus product-side indexes for listing/sort candidates.

Result: regression. `allLatencyReduced=false`.

| Scenario | Avg Before | Avg After | P95 Before | P95 After | P99 Before | P99 After |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| product latest | 7.816 ms | 34.426 ms | 10.830 ms | 93.140 ms | 17.141 ms | 137.052 ms |
| product price asc | 7.873 ms | 34.197 ms | 11.095 ms | 90.228 ms | 17.687 ms | 145.503 ms |
| product likes desc | 6.495 ms | 34.194 ms | 9.713 ms | 90.146 ms | 15.982 ms | 141.529 ms |
| brand latest | 7.778 ms | 34.017 ms | 10.887 ms | 90.452 ms | 17.589 ms | 138.998 ms |
| brand price asc | 7.080 ms | 34.041 ms | 10.360 ms | 89.769 ms | 16.300 ms | 139.696 ms |
| brand likes desc | 6.971 ms | 33.791 ms | 10.121 ms | 88.613 ms | 16.541 ms | 143.977 ms |

Guardrails:
- Before: `requests=23763`, `requestRate=198.02/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`
- After: `requests=23762`, `requestRate=198.00/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`

Plan summary:
- Before EXPLAIN: `productIndexMentions=0`, `statsIndex=false`, `stockIndex=false`, `filesort=3`
- After EXPLAIN: `productIndexMentions=12`, `statsIndex=true`, `stockIndex=false`, `filesort=3`

Conclusion: the broad index set made the optimizer choose a worse plan under this workload. Do not keep that index set.

Artifacts:
- `performance/results/product-list-index-before.json`
- `performance/results/product-list-index-before-summary.txt`
- `performance/results/product-list-index-after.json`
- `performance/results/product-list-index-after-summary.txt`
- `performance/results/product-list-index-comparison.json`
- `performance/results/product-list-index-comparison-summary.txt`
- `performance/results/product-list-explain-comparison-summary.txt`

## Experiment 2: `products(brand_id)` Only On Full Join Query

Hypothesis: for brand-filtered list queries, a single `products(brand_id)` index will let MySQL avoid scanning all products before joining stats/stock.

Execution notes:
- App source was run from a temporary `HEAD` export to preserve the original full-join query shape and avoid the dirty v2 query-split workspace.
- Dataset had `products=1,000,000`.
- Before product indexes: `products` had only `PRIMARY`.
- Existing `product_stats` indexes remained, including `idx_product_stats_deleted_like_count_product_id`.
- Runtime index added for after: `CREATE INDEX idx_products_brand_id ON products (brand_id)`.

### Query Plan

Representative SQL:

```sql
SELECT p.id, p.name, b.id, b.name, p.price,
       stats.like_count, stock.stock_quantity, stock.reserved_quantity
FROM products p
JOIN brands b
  ON p.brand_id = b.id
 AND b.deleted_at IS NULL
 AND b.status = 'ACTIVE'
JOIN product_stats stats
  ON stats.deleted_at IS NULL
 AND stats.product_id = p.id
JOIN product_stocks stock
  ON stock.deleted_at IS NULL
 AND stock.product_id = p.id
WHERE b.id = 1001
  AND p.deleted_at IS NULL
  AND p.status = 'ON_SALE'
ORDER BY stats.like_count DESC, p.created_at DESC
LIMIT 20 OFFSET 0;
```

Before `products(brand_id)`:
- `products`: table scan, actual rows scanned about `1,000,000`.
- `product_stats`: single-row lookup by `product_id`, `1000` loops.
- `product_stocks`: single-row lookup by `product_id`, `1000` loops.
- EXPLAIN ANALYZE elapsed for representative query: about `400 ms`.

After `products(brand_id)`:
- `products`: index lookup using `idx_products_brand_id`, actual rows about `1000`.
- `product_stats`: single-row lookup by `product_id`, `1000` loops.
- `product_stocks`: single-row lookup by `product_id`, `1000` loops.
- EXPLAIN ANALYZE elapsed for representative query: about `142 ms`.

The single-query plan improved, but the full six-scenario API load did not pass guardrails.

### Load Test

Result: regression / unstable under the standard 200 rps mixed workload. `allLatencyReduced=false`.

| Scenario | Avg Before | Avg After | P95 Before | P95 After | P99 Before | P99 After |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| product latest | 8687.100 ms | 9258.634 ms | 9869.866 ms | 10839.842 ms | 13301.916 ms | 59999.520 ms |
| product price asc | 8692.927 ms | 9263.786 ms | 9936.763 ms | 10837.380 ms | 13319.099 ms | 59999.176 ms |
| product likes desc | 10679.261 ms | 9313.250 ms | 10118.046 ms | 10829.773 ms | 60000.609 ms | 59999.559 ms |
| brand latest | 8542.856 ms | 8516.593 ms | 9664.582 ms | 9226.179 ms | 10372.521 ms | 10867.446 ms |
| brand price asc | 8545.272 ms | 8512.584 ms | 9665.084 ms | 9227.529 ms | 10597.795 ms | 10874.128 ms |
| brand likes desc | 8611.707 ms | 8514.504 ms | 9890.848 ms | 9219.383 ms | 11630.289 ms | 10865.340 ms |

Guardrails:
- Before: `requests=6803`, `requestRate=52.42/s`, `failedRate=96.31%`, `checksRate=3.69%`, `droppedIterations=16962`
- After: `requests=6817`, `requestRate=52.54/s`, `failedRate=99.43%`, `checksRate=0.57%`, `droppedIterations=16948`

Interpretation:
- `products(brand_id)` is useful for the representative brand-filtered SQL plan.
- It is not sufficient for the full mixed API workload because the global product list scenarios still use the full join path and saturate the app/DB.
- Because both before and after failed guardrails, the load-test latency numbers are not a stable success comparison. The only reliable positive result from this experiment is the representative `EXPLAIN ANALYZE` improvement for brand-filtered SQL.

Artifacts:
- `performance/results/product-brand-index-before-db-state.txt`
- `performance/results/product-brand-index-before-explain-brand-likes-desc.txt`
- `performance/results/product-brand-index-before.json`
- `performance/results/product-brand-index-before-summary.txt`
- `performance/results/product-brand-index-after-add-index.txt`
- `performance/results/product-brand-index-after-explain-brand-likes-desc.txt`
- `performance/results/product-brand-index-after.json`
- `performance/results/product-brand-index-after-summary.txt`
- `performance/results/product-brand-index-comparison.json`
- `performance/results/product-brand-index-comparison-summary.txt`

## Overall Recommendation

Do not adopt the broad index set from Experiment 1.

Do not claim `products(brand_id)` alone solves the API latency target. It improves the brand-filtered SQL access path, but the mixed API benchmark still fails badly. The next viable experiment should either:

1. isolate brand-list endpoints in their own load test to quantify the brand-index benefit without global-list saturation, or
2. change query shape/read model so global list and brand list do not rely on the same expensive full-join execution path.
