# Cart Count Redis Cache Performance Report

## Summary

Target: `GET /api/v1/cart/count`

Change: added Redis read-through caching for each user's cart item-line count, with cache invalidation after cart mutation transaction commit.

Result: endpoint latency decreased across the stable local k6 benchmark, with no request failures, custom count-check failures, or dropped iterations.

## Setup

- Date: 2026-06-19
- App profile used for benchmark: `local-dev`
- Data store: local Docker MySQL and Redis from `docker/infra-compose.yml`
- Seed data: `performance/sql/cart-count-seed.sql`
- Seed shape:
  - `1000` cart users
  - `1000` carts
  - `10000` cart items
  - `10` cart item lines per user
- Benchmark script: `performance/k6/cart-count.js`
- Stable benchmark command:

```shell
BASE_URL=http://127.0.0.1:8080 \
K6_RATE=20 \
K6_PRE_ALLOCATED_VUS=20 \
K6_MAX_VUS=100 \
USER_COUNT=1000 \
EXPECTED_COUNT=10 \
k6 run --summary-export=<result>.json performance/k6/cart-count.js
```

An initial `200 rps` run overloaded the local DB connection pool and was preserved as an overload guardrail artifact. The stable before/after comparison uses `20 rps` for both runs.

## Before / After

| Metric | Before | After | Delta | Change |
| --- | ---: | ---: | ---: | ---: |
| Average latency | 153.043 ms | 101.637 ms | -51.406 ms | -33.6% |
| p50 latency | 106.606 ms | 100.892 ms | -5.714 ms | -5.4% |
| p90 latency | 166.670 ms | 104.224 ms | -62.446 ms | -37.5% |
| p95 latency | 506.934 ms | 106.146 ms | -400.788 ms | -79.1% |
| p99 latency | 837.150 ms | 112.769 ms | -724.381 ms | -86.5% |
| Max latency | 997.074 ms | 213.402 ms | -783.672 ms | -78.6% |
| Requests | 2401 | 2401 | 0 | 0.0% |
| Request rate | 19.991/s | 19.991/s | 0.000/s | 0.0% |
| Failed rate | 0.0000 | 0.0000 | 0.0000 | 0.0% |
| Custom error rate | 0.0000 | 0.0000 | 0.0000 | 0.0% |
| Dropped iterations | 0 | 0 | 0 | 0.0% |

Redis DB size after the final run: `1000`, matching one cached cart-count key per seeded user.

## Correctness

Implemented invalidation after transaction commit. This avoids the stale-cache race where a mutation deletes the key before commit, a concurrent count request repopulates the old committed count, and the stale value remains in Redis.

Verification artifacts:

- `performance/results/cart-count-cache-red-test.txt`
- `performance/results/cart-count-cache-transaction-red-test.txt`
- `performance/results/cart-count-cache-transaction-green-test.txt`
- `performance/results/cart-count-shopping-tests-final.txt`
- `performance/results/cart-count-ktlint-check-final.txt`

Final verification commands:

```shell
./gradlew :apps:commerce-api:test --tests "com.loopers.application.shopping.*" --tests "com.loopers.interfaces.api.shopping.CartV1ApiE2ETest"
./gradlew ktlintCheck
```

Both final commands completed successfully.

## Artifacts

- Baseline summary: `performance/results/cart-count-before-summary.txt`
- Final after summary: `performance/results/cart-count-after-final-summary.txt`
- Comparison: `performance/results/cart-count-comparison.txt`
- Baseline k6 JSON: `performance/results/cart-count-before.json`
- Final after k6 JSON: `performance/results/cart-count-after-final.json`
- Initial overload run: `performance/results/cart-count-before-overload.json`
- Redis DB size guardrail: `performance/results/cart-count-redis-dbsize-after-final.txt`
- Seed verification: `performance/results/cart-count-dev-seed-final.txt`

## Residual Risks

- This report proves improvement for one local stable-load benchmark, not every production-like traffic pattern.
- Redis delete failures are not retried; a delete failure could leave stale data until a later cart mutation.
- Cache entries currently have no TTL. This is intentionally minimal for the requested change, but TTL may be useful as a safety guardrail.
