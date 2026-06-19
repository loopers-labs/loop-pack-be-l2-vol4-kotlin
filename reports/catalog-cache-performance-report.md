# Catalog Redis Cache Performance Report

Date: 2026-06-19

## Target

- `GET /api/v1/products`
- `GET /api/v1/products/{productId}`

Product detail cache is applied only to product item data and detail image data. User-specific view data such as
`likedByMe` remains outside the cache and is still composed per request.

Product list item-cache usage was benchmarked, but it was rolled back because the measured list latency regressed on the
local fixture. The final product list implementation delegates directly to the DAO and does not read product item cache.

## Implementation Summary

- Added a primary `CachedCatalogProductQueryPort` decorator around the existing catalog query DAO.
- Cached product detail product rows and product detail image lists in Redis.
- Kept product list and brand product list reads uncached after the k6 regression.
- Kept page, sort, and user-specific API responses uncached.
- Added cache invalidation hooks for product, brand, stock, like-count, and order stock changes.
- Extended the local in-memory Redis test template to support `set(key, value, Duration)`.

## Cache Design

TTL: 5 minutes.

Keys:

- `catalog:product:item:v1:{productId}`
- `catalog:product:detail-images:v1:{productId}`
- `catalog:brand-products:v1:{brandId}`

Invalidation:

- Product item cache is evicted when product data, status, stock, reservation, actual stock, or like count changes.
- Product detail image cache is evicted with the product item cache.
- Brand product cache tracking is used to evict cached items when a brand is updated, activated, deactivated, or deleted.

## Benchmark Setup

Environment:

- `SPRING_PROFILES_ACTIVE=test`
- Docker MySQL `loopers` database
- Docker Redis master/replica
- Seed data: `docker/mysql/init/02-test-data.sql`

k6 commands:

```shell
BASE_URL=http://localhost:8080 PEAK_VUS=100 SORT=latest PAGE=0 SIZE=20 \
  k6 run --summary-export performance/results/products-list-before.json performance/k6/products-list.js

BASE_URL=http://localhost:8080 PEAK_VUS=100 SORT=latest PAGE=0 SIZE=20 \
  k6 run --summary-export performance/results/products-list-after.json performance/k6/products-list.js

BASE_URL=http://localhost:8080 PEAK_VUS=100 PRODUCT_ID=1 \
  k6 run --summary-export performance/results/product-detail-before.json performance/k6/product-detail.js

BASE_URL=http://localhost:8080 PEAK_VUS=100 PRODUCT_ID=1 \
  k6 run --summary-export performance/results/product-detail-after.json performance/k6/product-detail.js
```

## Before / After

| Target | Metric | Before | After | Delta |
| --- | ---: | ---: | ---: | ---: |
| Product list | avg | 14.11ms | 17.18ms | +3.06ms (+21.70%) |
| Product list | p95 | 22.94ms | 33.03ms | +10.09ms (+44.00%) |
| Product list | failed rate | 0% | 0% | 0 |
| Product list | requests | 14,840 | 14,774 | -66 |
| Product detail | avg | 15.59ms | 13.51ms | -2.08ms (-13.33%) |
| Product detail | p95 | 28.19ms | 29.17ms | +0.98ms (+3.47%) |
| Product detail | failed rate | 0% | 0% | 0 |
| Product detail | requests | 14,821 | 14,852 | +31 |

Both endpoints stayed below the k6 threshold of `http_req_duration p95 < 500ms`.

The product list "After" numbers are from the rejected item-cache experiment, not the final rolled-back list
implementation. The product list p95 regression is the reason list caching was removed. Product detail average latency
improved while p95 was nearly flat. The likely cause is that the local fixture is small enough that Redis round trips and
JSON serialization offset the saved database work for list reads.

## Verification

Passed:

```shell
./gradlew :apps:commerce-api:test \
  --tests "com.loopers.infrastructure.catalog.CachedCatalogProductQueryPortTest" \
  --tests "com.loopers.application.catalog.CatalogApplicationServiceTest" \
  --tests "com.loopers.CommerceApiContextTest"

./gradlew ktlintCheck
```

Verification artifacts:

- `performance/results/verification-tests.txt`
- `performance/results/verification-ktlint.txt`

Benchmark artifacts:

- `performance/results/cache-comparison-summary.json`
- `performance/results/products-list-before.json`
- `performance/results/products-list-before.txt`
- `performance/results/products-list-after.json`
- `performance/results/products-list-after.txt`
- `performance/results/product-detail-before.json`
- `performance/results/product-detail-before.txt`
- `performance/results/product-detail-after.json`
- `performance/results/product-detail-after.txt`

## Residual Risks

- The benchmark used a very small local dataset, so it may understate the value of item-level caching for production-sized product detail rows or heavier joins.
- Product list is currently uncached after rollback. Reintroducing list acceleration should be measured with a larger
  fixture and a design that avoids per-item Redis overhead on small pages.
- Cache hit rate was not exported as a measured metric. The implementation was verified behaviorally through unit tests and Redis-backed k6 runs.
