# Product List Likes Desc Index Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure `GET /api/v1/products?sort=likes_desc` under a 2-minute k6 peak load, add the database index that supports like-count-desc product listing, then rerun the same load and compare before/after results.

**Architecture:** Keep the API and JPQL query behavior unchanged. Add an index to `product_stats` because `CatalogProductQueryDao` orders the product list by `stats.likeCount desc, p.createdAt desc`, and `product_stats` owns `like_count`. Use a schema-level integration test to prove Hibernate-created test schemas include the index, and update the Docker bootstrap schema so local MySQL validates and fresh local databases get the same index.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, Hibernate/JPA, MySQL 8.0, Gradle, JUnit 5, Testcontainers, k6 via `grafana/k6` Docker image.

---

## Assumptions And Tradeoffs

- Target endpoint is the public product list: `GET /api/v1/products?sort=likes_desc&page=0&size=20`.
- This plan does not change brand-product listing (`GET /api/v1/brands/{brandId}/products`) unless a separate requirement is added.
- "Peak request for about 2 mins" is interpreted as a constant arrival rate for exactly 2 minutes. The plan uses `K6_RATE=200` requests/second, `K6_PRE_ALLOCATED_VUS=100`, and `K6_MAX_VUS=500` for both before and after runs.
- The chosen index is `product_stats(deleted_at, like_count DESC, product_id)`. `deleted_at` is first because the query filters `stats.deletedAt is null`; `like_count DESC` matches the requested sort; `product_id` supports the join back to `products`.
- The query also has a tie-breaker on `p.createdAt desc`, which lives in another table. A single index on `product_stats` cannot fully cover that cross-table ordering. If EXPLAIN or k6 shows little improvement, stop after this plan and propose a separate query-design plan rather than changing ordering semantics silently.
- The baseline data setup recreates the local Docker MySQL volume. This deletes local data in the `docker/infra-compose.yml` MySQL volume.

## File Structure

- Create: `performance/k6/product-list-likes-desc.js` - k6 scenario for the likes-desc product list endpoint.
- Create: `performance/results/.gitkeep` - keeps the ignored result directory in git.
- Modify: `.gitignore` - ignores generated k6/EXPLAIN result files.
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStatsSchemaIntegrationTest.kt` - proves the index exists in Hibernate-created test schemas.
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStats.kt` - declares the JPA index.
- Modify: `docker/mysql/init/01-schema.sql` - adds the same index to the local Docker bootstrap schema.

## Task 1: Add The k6 Stress Script

**Files:**
- Create: `performance/k6/product-list-likes-desc.js`
- Create: `performance/results/.gitkeep`
- Modify: `.gitignore`

- [ ] **Step 1: Create the k6 script**

Create `performance/k6/product-list-likes-desc.js` with:

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const page = __ENV.PAGE || '0';
const size = __ENV.SIZE || '20';
const rate = Number(__ENV.K6_RATE || '200');
const preAllocatedVUs = Number(__ENV.K6_PRE_ALLOCATED_VUS || '100');
const maxVUs = Number(__ENV.K6_MAX_VUS || '500');

export const productListLikesDescLatency = new Trend('product_list_likes_desc_latency');
export const productListLikesDescErrors = new Rate('product_list_likes_desc_errors');

export const options = {
  scenarios: {
    product_list_likes_desc_peak: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    product_list_likes_desc_errors: ['rate<0.01'],
  },
};

export default function () {
  const url = `${baseUrl}/api/v1/products?sort=likes_desc&page=${page}&size=${size}`;
  const response = http.get(url, {
    tags: {
      endpoint: 'products_likes_desc',
    },
  });

  productListLikesDescLatency.add(response.timings.duration);
  productListLikesDescErrors.add(response.status >= 400);

  check(response, {
    'status is 200': (res) => res.status === 200,
    'body contains data envelope': (res) => res.body.includes('"data"'),
  });
}
```

- [ ] **Step 2: Keep the result directory tracked without committing generated output**

Run:

```bash
mkdir -p performance/results
touch performance/results/.gitkeep
```

Append this block to `.gitignore`:

```gitignore

### Local performance results ###
/performance/results/*.json
/performance/results/*.txt
!/performance/results/.gitkeep
```

- [ ] **Step 3: Run a k6 syntax check**

Run:

```bash
docker run --rm -v "$PWD/performance/k6:/scripts:ro" grafana/k6 inspect /scripts/product-list-likes-desc.js
```

Expected: exit code `0`, and output includes `product_list_likes_desc_peak`.

- [ ] **Step 4: Commit the load-test harness**

Run:

```bash
git add .gitignore performance/k6/product-list-likes-desc.js performance/results/.gitkeep
git commit -m "test: add product list likes desc k6 scenario"
```

Expected: commit succeeds and `git status --short` no longer lists those three paths.

## Task 2: Capture Baseline Performance Before The Index

**Files:**
- Uses: `src/main/kotlin/com/loopers/tools/devdata/GenerateCatalogData.kt`
- Uses: `docker/mysql/init/03-load-generated-catalog.sh`
- Generates ignored files under: `performance/results/`

- [ ] **Step 1: Generate deterministic catalog seed data**

Run:

```bash
./gradlew generateCatalogCsv -PcatalogCsvBrandCount=1000 -PcatalogCsvProductCount=1000000 -PcatalogCsvSeed=20260619
```

Expected: exit code `0`, and these ignored files exist:

```text
docker/mysql/generated/brands.csv
docker/mysql/generated/products.csv
docker/mysql/generated/product_stocks.csv
docker/mysql/generated/product_stats.csv
```

- [ ] **Step 2: Recreate the local MySQL dataset without the new index**

Run:

```bash
docker-compose -f docker/infra-compose.yml down -v
docker-compose -f docker/infra-compose.yml up -d mysql redis-master redis-readonly kafka kafka-ui
```

Expected: exit code `0`.

Then run:

```bash
docker-compose -f docker/infra-compose.yml logs mysql | tail -n 80
```

Expected: output includes `Generated catalog CSV load complete.`.

- [ ] **Step 3: Start the commerce API**

In a separate terminal, run:

```bash
./gradlew :apps:commerce-api:bootRun
```

Expected: the application starts on port `8080` without schema validation errors.

- [ ] **Step 4: Verify the endpoint responds**

Run:

```bash
curl -fsS "http://localhost:8080/api/v1/products?sort=likes_desc&page=0&size=20" > /tmp/product-list-likes-desc-before-response.json
```

Expected: exit code `0`, and `/tmp/product-list-likes-desc-before-response.json` contains `"data"`.

- [ ] **Step 5: Capture baseline EXPLAIN**

Run:

```bash
docker-compose -f docker/infra-compose.yml exec mysql mysql -uapplication -papplication loopers -e "EXPLAIN FORMAT=JSON select p.id, p.name, b.id, b.name, p.price, stats.like_count, stock.stock_quantity, stock.reserved_quantity from products p, brands b, product_stats stats, product_stocks stock where p.deleted_at is null and p.status = 'ON_SALE' and b.deleted_at is null and b.status = 'ACTIVE' and p.brand_id = b.id and stats.deleted_at is null and stats.product_id = p.id and stock.deleted_at is null and stock.product_id = p.id order by stats.like_count desc, p.created_at desc limit 20 offset 0" > performance/results/product-list-likes-desc-explain-before.json
```

Expected: exit code `0`, and `performance/results/product-list-likes-desc-explain-before.json` exists.

- [ ] **Step 6: Run the 2-minute baseline k6 stress test**

Run:

```bash
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -e K6_RATE=200 -e K6_PRE_ALLOCATED_VUS=100 -e K6_MAX_VUS=500 -v "$PWD/performance/k6:/scripts:ro" -v "$PWD/performance/results:/results" grafana/k6 run --summary-export=/results/product-list-likes-desc-before.json /scripts/product-list-likes-desc.js
```

Expected: k6 runs for `2m0s`, exits `0`, and writes `performance/results/product-list-likes-desc-before.json`.

- [ ] **Step 7: Extract the baseline summary**

Run:

```bash
node -e 'const r=require("./performance/results/product-list-likes-desc-before.json"); const m=r.metrics; console.log(JSON.stringify({requests:m.http_reqs?.count, failedRate:m.http_req_failed?.rate, p95:m.http_req_duration?.["p(95)"], avg:m.http_req_duration?.avg}, null, 2));'
```

Expected: output includes numeric `requests`, `failedRate`, `p95`, and `avg` values.

## Task 3: Write The Failing Schema Test

**Files:**
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStatsSchemaIntegrationTest.kt`

- [ ] **Step 1: Add the index-existence integration test**

Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStatsSchemaIntegrationTest.kt` with:

```kotlin
package com.loopers.infrastructure.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class ProductStatsSchemaIntegrationTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    @DisplayName("product_stats 는 좋아요 수 내림차순 상품 목록 조회용 인덱스를 가진다.")
    @Test
    fun hasLikesDescProductListIndex() {
        val columns = jdbcTemplate.query(
            """
            select column_name, collation, seq_in_index
              from information_schema.statistics
             where table_schema = database()
               and table_name = 'product_stats'
               and index_name = 'idx_product_stats_deleted_like_count_product_id'
             order by seq_in_index
            """.trimIndent(),
        ) { rs, _ ->
            IndexColumn(
                name = rs.getString("column_name"),
                collation = rs.getString("collation"),
                sequence = rs.getInt("seq_in_index"),
            )
        }

        assertThat(columns).containsExactly(
            IndexColumn(name = "deleted_at", collation = "A", sequence = 1),
            IndexColumn(name = "like_count", collation = "D", sequence = 2),
            IndexColumn(name = "product_id", collation = "A", sequence = 3),
        )
    }

    private data class IndexColumn(
        val name: String,
        val collation: String,
        val sequence: Int,
    )
}
```

- [ ] **Step 2: Run the schema test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.ProductStatsSchemaIntegrationTest"
```

Expected: test fails because `columns` is empty and does not contain the expected index columns.

## Task 4: Add The Product Stats Index

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStats.kt`
- Modify: `docker/mysql/init/01-schema.sql`

- [ ] **Step 1: Declare the JPA index on `ProductStats`**

Replace the imports and `@Table` declaration in `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStats.kt` with:

```kotlin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_stats",
    indexes = [
        Index(
            name = "idx_product_stats_deleted_like_count_product_id",
            columnList = "deleted_at, like_count DESC, product_id",
        ),
    ],
)
class ProductStats(
```

Leave the constructor and methods unchanged.

- [ ] **Step 2: Add the same index to the Docker bootstrap schema**

In `docker/mysql/init/01-schema.sql`, replace the `product_stats` table keys:

```sql
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_stats_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

with:

```sql
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_stats_product_id (product_id),
    KEY idx_product_stats_deleted_like_count_product_id (deleted_at, like_count DESC, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

- [ ] **Step 3: Run the schema test and verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.ProductStatsSchemaIntegrationTest"
```

Expected: test passes.

- [ ] **Step 4: Run the existing catalog DAO test**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.CatalogProductQueryDaoIntegrationTest"
```

Expected: test passes, proving likes-desc product listing behavior did not change.

- [ ] **Step 5: Run lint for touched Kotlin files**

Run:

```bash
./gradlew ktlintCheck
```

Expected: exit code `0`.

- [ ] **Step 6: Commit the index and schema test**

Run:

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStats.kt apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStatsSchemaIntegrationTest.kt docker/mysql/init/01-schema.sql
git commit -m "perf: index product stats for likes desc listing"
```

Expected: commit succeeds and `git status --short` no longer lists those three paths.

## Task 5: Apply The Index To The Existing Benchmark Database

**Files:**
- Uses existing local Docker MySQL data from Task 2.
- Generates ignored files under: `performance/results/`

- [ ] **Step 1: Stop the running commerce API**

Stop the `./gradlew :apps:commerce-api:bootRun` process from Task 2 with `Ctrl-C`.

Expected: the API process exits cleanly.

- [ ] **Step 2: Add the index to the already-loaded local MySQL dataset**

Run:

```bash
docker-compose -f docker/infra-compose.yml exec mysql mysql -uroot -proot loopers -e "ALTER TABLE product_stats ADD INDEX idx_product_stats_deleted_like_count_product_id (deleted_at, like_count DESC, product_id)"
```

Expected: exit code `0`.

- [ ] **Step 3: Verify the index exists in local MySQL**

Run:

```bash
docker-compose -f docker/infra-compose.yml exec mysql mysql -uapplication -papplication loopers -e "SHOW INDEX FROM product_stats WHERE Key_name = 'idx_product_stats_deleted_like_count_product_id'"
```

Expected: output includes three rows for `deleted_at`, `like_count`, and `product_id`.

- [ ] **Step 4: Restart the commerce API with the indexed schema**

Run:

```bash
./gradlew :apps:commerce-api:bootRun
```

Expected: the application starts on port `8080` without schema validation errors.

- [ ] **Step 5: Capture after-index EXPLAIN**

Run:

```bash
docker-compose -f docker/infra-compose.yml exec mysql mysql -uapplication -papplication loopers -e "EXPLAIN FORMAT=JSON select p.id, p.name, b.id, b.name, p.price, stats.like_count, stock.stock_quantity, stock.reserved_quantity from products p, brands b, product_stats stats, product_stocks stock where p.deleted_at is null and p.status = 'ON_SALE' and b.deleted_at is null and b.status = 'ACTIVE' and p.brand_id = b.id and stats.deleted_at is null and stats.product_id = p.id and stock.deleted_at is null and stock.product_id = p.id order by stats.like_count desc, p.created_at desc limit 20 offset 0" > performance/results/product-list-likes-desc-explain-after.json
```

Expected: exit code `0`, and `performance/results/product-list-likes-desc-explain-after.json` exists.

## Task 6: Capture After-Index Stress Results And Compare

**Files:**
- Uses: `performance/k6/product-list-likes-desc.js`
- Generates ignored files under: `performance/results/`

- [ ] **Step 1: Run the same 2-minute k6 stress test after applying the index**

Run:

```bash
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -e K6_RATE=200 -e K6_PRE_ALLOCATED_VUS=100 -e K6_MAX_VUS=500 -v "$PWD/performance/k6:/scripts:ro" -v "$PWD/performance/results:/results" grafana/k6 run --summary-export=/results/product-list-likes-desc-after.json /scripts/product-list-likes-desc.js
```

Expected: k6 runs for `2m0s`, exits `0`, and writes `performance/results/product-list-likes-desc-after.json`.

- [ ] **Step 2: Print a before/after comparison**

Run:

```bash
node -e 'const before=require("./performance/results/product-list-likes-desc-before.json").metrics; const after=require("./performance/results/product-list-likes-desc-after.json").metrics; const read=(m)=>({requests:m.http_reqs?.count, failedRate:m.http_req_failed?.rate, avg:m.http_req_duration?.avg, p90:m.http_req_duration?.["p(90)"], p95:m.http_req_duration?.["p(95)"], p99:m.http_req_duration?.["p(99)"]}); console.log(JSON.stringify({before:read(before), after:read(after)}, null, 2));'
```

Expected: output includes before and after `requests`, `failedRate`, `avg`, `p90`, `p95`, and `p99`.

- [ ] **Step 3: Compare EXPLAIN output**

Run:

```bash
node -e 'const fs=require("fs"); const before=fs.readFileSync("performance/results/product-list-likes-desc-explain-before.json","utf8"); const after=fs.readFileSync("performance/results/product-list-likes-desc-explain-after.json","utf8"); console.log("before mentions index:", before.includes("idx_product_stats_deleted_like_count_product_id")); console.log("after mentions index:", after.includes("idx_product_stats_deleted_like_count_product_id")); console.log("before mentions filesort:", before.toLowerCase().includes("filesort")); console.log("after mentions filesort:", after.toLowerCase().includes("filesort"));'
```

Expected: `before mentions index: false` and `after mentions index: true`. `after mentions filesort` may remain `true` because `p.created_at desc` is ordered from the joined `products` table.

- [ ] **Step 4: Check for generated files before final reporting**

Run:

```bash
git status --short
```

Expected: `performance/results/product-list-likes-desc-before.json`, `performance/results/product-list-likes-desc-after.json`, `performance/results/product-list-likes-desc-explain-before.json`, and `performance/results/product-list-likes-desc-explain-after.json` are not listed because `.gitignore` ignores them.

- [ ] **Step 5: Report the measured result**

In the final implementation response, include these concrete facts and copy the numeric metrics printed by Step 2 and Step 3:

```text
Endpoint: GET /api/v1/products?sort=likes_desc&page=0&size=20
Dataset: 1,000 brands, 1,000,000 products, seed 20260619
Load: constant-arrival-rate, 200 req/s, 2 minutes
Before metrics: copy the JSON object under "before" from Step 2.
After metrics: copy the JSON object under "after" from Step 2.
EXPLAIN: copy the four booleans printed by Step 3.
```

## Verification Commands

Run these before marking the implementation complete:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.ProductStatsSchemaIntegrationTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.CatalogProductQueryDaoIntegrationTest"
./gradlew ktlintCheck
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -e K6_RATE=200 -e K6_PRE_ALLOCATED_VUS=100 -e K6_MAX_VUS=500 -v "$PWD/performance/k6:/scripts:ro" -v "$PWD/performance/results:/results" grafana/k6 run --summary-export=/results/product-list-likes-desc-after.json /scripts/product-list-likes-desc.js
```

## Self-Review

- Spec coverage: The plan tests current performance, adds the index, and tests performance after applying the index.
- Placeholder scan: The plan contains concrete paths, commands, index names, endpoint, dataset size, and k6 load parameters.
- Type consistency: The index name is consistently `idx_product_stats_deleted_like_count_product_id`; the target endpoint is consistently `GET /api/v1/products?sort=likes_desc&page=0&size=20`.
