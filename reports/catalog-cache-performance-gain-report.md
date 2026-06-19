# Catalog Cache Performance Gain Report

Date: 2026-06-19

## Scope

- Accepted final cache target: `GET /api/v1/products/{productId}`
- Rolled-back cache target: `GET /api/v1/products` and `GET /api/v1/brands/{brandId}/products`

Redis caching remains enabled for product detail item data and detail image data. Product list item-cache usage was removed
after the benchmark because it regressed latency in the measured environment.

## Benchmark Environment

- Profile: `SPRING_PROFILES_ACTIVE=test`
- Database: Docker MySQL `loopers`
- Cache: Docker Redis master/replica
- Seed data: `docker/mysql/init/02-test-data.sql`
- Load tool: k6
- Peak VUs: 100

## Accepted Performance Gain

| Target | Metric | Before | After | Delta | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Product detail | avg latency | 15.59ms | 13.51ms | -2.08ms (-13.33%) | Improved |
| Product detail | p95 latency | 28.19ms | 29.17ms | +0.98ms (+3.47%) | Slight regression |
| Product detail | max latency | 351.74ms | 323.18ms | -28.56ms (-8.12%) | Improved |
| Product detail | failed rate | 0% | 0% | 0 | Stable |
| Product detail | requests | 14,821 | 14,852 | +31 | Stable |

Primary accepted gain: product detail average latency improved by 2.08ms, or 13.33%.

Guardrail: product detail p95 latency regressed by 0.98ms. This is small in absolute terms and stayed far below the
configured k6 threshold of `http_req_duration p95 < 500ms`, but it should be rechecked with a larger production-like
fixture before expanding cache scope.

## Rejected Product List Experiment

| Target | Metric | Before | After cache experiment | Delta | Decision |
| --- | ---: | ---: | ---: | ---: | --- |
| Product list | avg latency | 14.11ms | 17.18ms | +3.06ms (+21.70%) | Rolled back |
| Product list | p95 latency | 22.94ms | 33.03ms | +10.09ms (+44.00%) | Rolled back |
| Product list | failed rate | 0% | 0% | 0 | Stable |
| Product list | requests | 14,840 | 14,774 | -66 | Slight decrease |

The product list cache experiment did not produce a performance gain. The final implementation delegates product list
and brand product list reads directly to the DAO and does not read product item cache for list responses.

## Evidence

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

Verification commands:

```shell
./gradlew :apps:commerce-api:test \
  --tests "com.loopers.infrastructure.catalog.CachedCatalogProductQueryPortTest" \
  --tests "com.loopers.application.catalog.CatalogApplicationServiceTest" \
  --tests "com.loopers.CommerceApiContextTest"

./gradlew ktlintCheck
```

Both commands completed with `BUILD SUCCESSFUL` before this report was saved.

## Conclusion

The accepted cache change provides a measured average-latency gain for product detail reads. Product list caching was not
kept because the k6 result showed worse average and p95 latency under the measured local workload.
