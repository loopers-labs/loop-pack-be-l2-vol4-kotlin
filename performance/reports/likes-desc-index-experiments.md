# Brand Likes Desc Index Experiments

Date: 2026-06-19

Target:

```http
GET /api/v1/brands/{brandId}/products?sort=likes_desc&page=0&size=20
```

Benchmark command:

```bash
BASE_URL=http://localhost:18080 BRAND_ID=1001 k6 run --summary-export <summary.json> performance/k6/product-list-likes-desc.js
```

Primary metric: `brand_product_list_likes_desc_latency`.

Guardrails: `http_req_failed`, `product_list_errors`, `checks`, `dropped_iterations`, and request rate.

Dataset used by the representative EXPLAIN runs:

| Table | Rows |
| --- | ---: |
| `products` | 1,000,000 |
| `brands` | 1,000 |
| `product_stats` | 1,000,000 |
| `product_stocks` | 1,000,000 |

Runtime indexes relevant to this report:

- `products.idx_products_brand_id(brand_id)`
- `product_stats.idx_product_stats_deleted_like_count_product_id(deleted_at, like_count DESC, product_id)`
- `product_stats` unique index on `product_id`
- `product_stocks` unique index on `product_id`

## Experiment 1: Broad Index Set On Full Join Query

Hypothesis: adding query-specific indexes to the existing four-table full join would reduce brand `likes_desc` latency.

Query shape:

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

Result: regression.

| Metric | Before | After | Delta | Delta % |
| --- | ---: | ---: | ---: | ---: |
| avg | 6.971 ms | 33.791 ms | +26.819 ms | +384.7% |
| med | 6.558 ms | 24.605 ms | +18.047 ms | +275.2% |
| p90 | 8.991 ms | 67.986 ms | +58.995 ms | +656.2% |
| p95 | 10.121 ms | 88.613 ms | +78.491 ms | +775.5% |
| p99 | 16.541 ms | 143.977 ms | +127.435 ms | +770.4% |
| max | 165.722 ms | 314.753 ms | +149.031 ms | +89.9% |

Guardrails passed in both runs:

- Before: `requests=23763`, `requestRate=198.02/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`
- After: `requests=23762`, `requestRate=198.00/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`

Interpretation: the broad index set made the measured endpoint slower. Do not keep the broad index set for this query.

Artifacts:

- `performance/results/product-list-index-before.json`
- `performance/results/product-list-index-after.json`
- `performance/results/product-list-index-comparison.json`

## Experiment 2: `products(brand_id)` Only On The Full Join Query

Hypothesis: a single `products(brand_id)` index should let MySQL avoid scanning all products for the brand-filtered query.

Representative EXPLAIN from the earlier full-join experiment:

| Plan | Product access | EXPLAIN ANALYZE elapsed |
| --- | --- | ---: |
| Before `products(brand_id)` | table scan, about 1,000,000 rows | about 400 ms |
| After `products(brand_id)` | index lookup, about 1,000 rows | about 142 ms |

Representative SQL plan improved, but the load test run was not a stable success comparison because both before and after failed guardrails under the mixed workload.

| Metric | Before | After | Delta | Delta % |
| --- | ---: | ---: | ---: | ---: |
| avg | 8611.707 ms | 8514.504 ms | -97.203 ms | -1.1% |
| med | 8886.870 ms | 8857.486 ms | -29.384 ms | -0.3% |
| p90 | 9404.014 ms | 9188.805 ms | -215.209 ms | -2.3% |
| p95 | 9890.848 ms | 9219.383 ms | -671.465 ms | -6.8% |
| p99 | 11630.289 ms | 10865.340 ms | -764.949 ms | -6.6% |
| max | 13228.934 ms | 10890.237 ms | -2338.697 ms | -17.7% |

Guardrails failed in both runs:

- Before: `requests=6803`, `requestRate=52.42/s`, `failedRate=96.31%`, `checksRate=3.69%`, `droppedIterations=16962`
- After: `requests=6817`, `requestRate=52.54/s`, `failedRate=99.43%`, `checksRate=0.57%`, `droppedIterations=16948`

Interpretation: `products(brand_id)` is useful for the SQL access path, but this load-test comparison is too saturated to use as API latency proof.

Artifacts:

- `performance/results/product-brand-index-before-explain-brand-likes-desc.txt`
- `performance/results/product-brand-index-after-explain-brand-likes-desc.txt`
- `performance/results/product-brand-index-before.json`
- `performance/results/product-brand-index-after.json`
- `performance/results/product-brand-index-comparison.json`

## Experiment 3: Brand Query Split With `products(brand_id)`

Hypothesis: keep `products(brand_id)`, fetch the sorted product page from `Product + ProductStats`, then fetch `Brand` and `Stock` by bulk `IN` queries. This should reduce unnecessary stock lookups from 1,000 rows to the 20 products on the page.

Before query shape: one full join.

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

After query shape: three queries.

```sql
SELECT p.id, p.name, p.brand_id, p.price, stats.like_count
FROM products p
JOIN product_stats stats
  ON stats.deleted_at IS NULL
 AND stats.product_id = p.id
WHERE p.deleted_at IS NULL
  AND p.status = 'ON_SALE'
  AND p.brand_id = 1001
ORDER BY stats.like_count DESC, p.created_at DESC
LIMIT 20 OFFSET 0;
```

```sql
SELECT b.id, b.name
FROM brands b
WHERE b.deleted_at IS NULL
  AND b.status = 'ACTIVE'
  AND b.id IN (1001);
```

```sql
SELECT stock.product_id, stock.stock_quantity, stock.reserved_quantity
FROM product_stocks stock
WHERE stock.deleted_at IS NULL
  AND stock.product_id IN (<20 page product ids>);
```

### Representative Query Plan

The query-plan comparison below isolates the query-shape change. The old full-join query and new split page query both run on a runtime DB where `products.idx_products_brand_id` exists.

| Query | EXPLAIN ANALYZE elapsed | Key access pattern |
| --- | ---: | --- |
| Old full join | 52.1 ms | `products.idx_products_brand_id` reads 1,000 products; stats 1,000 lookups; stock 1,000 lookups |
| New page query | 11.3 ms | `products.idx_products_brand_id` reads 1,000 products; stats 1,000 lookups |
| New brand bulk query | ~0 ms | primary-key fetch for one brand |
| New stock bulk query | 0.218 ms | unique index range scan for 20 product ids |

Representative total for the after shape is about `11.5 ms`, down from `52.1 ms`.

| Metric | Before | After | Delta | Delta % |
| --- | ---: | ---: | ---: | ---: |
| EXPLAIN elapsed | 52.1 ms | 11.5 ms | -40.6 ms | -77.9% |

### Load Test

The stable after run had all guardrails passing and improved every tracked brand `likes_desc` latency statistic.

| Metric | Before | After | Delta | Delta % |
| --- | ---: | ---: | ---: | ---: |
| min | 3.625 ms | 3.360 ms | -0.265 ms | -7.3% |
| avg | 10.285 ms | 9.633 ms | -0.652 ms | -6.3% |
| med | 9.882 ms | 9.550 ms | -0.332 ms | -3.4% |
| p90 | 13.223 ms | 12.362 ms | -0.861 ms | -6.5% |
| p95 | 14.843 ms | 13.784 ms | -1.059 ms | -7.1% |
| p99 | 20.113 ms | 18.593 ms | -1.521 ms | -7.6% |
| max | 185.471 ms | 162.176 ms | -23.295 ms | -12.6% |

Guardrails passed:

- Before: `requests=23765`, `requestRate=198.01/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`
- After: `requests=23764`, `requestRate=198.02/s`, `failedRate=0`, `checksRate=1`, `droppedIterations=0`

Interpretation: for the brand `likes_desc` endpoint, the split query shape is the best result among the measured experiments. The SQL plan improves substantially, and the stable load test shows a smaller but consistent latency improvement.

Artifacts:

- `performance/results/product-list-v2-before-warm.json`
- `performance/results/product-list-v2-brand-only-no-exists-after.json`
- `performance/results/product-list-v2-brand-only-no-exists-comparison.json`
- `performance/results/product-list-v2-explain-db-context.txt`
- `performance/results/product-list-v2-explain-old-brand-fulljoin-likes-desc.txt`
- `performance/results/product-list-v2-explain-new-brand-split-likes-desc.txt`

## Conclusion

For `GET /api/v1/brands/{brandId}/products?sort=likes_desc&page=0&size=20`:

1. The broad index set regressed latency and should not be adopted.
2. `products(brand_id)` improves the brand-filtered access path, but the saturated mixed-load run is not reliable API proof.
3. Combining `products(brand_id)` with the brand query split gives the best evidence: representative SQL elapsed time improved by about `77.9%`, and the stable k6 run improved avg latency by `6.3%`, p95 by `7.1%`, and p99 by `7.6%`.

The durable lesson is that the useful index is simple, but the bigger win comes from reducing the row set that participates in stock lookup. For this endpoint, page first, then bulk fetch display data.
