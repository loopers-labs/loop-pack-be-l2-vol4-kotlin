# Product List Query Split V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce product list API latency by selecting the sorted product page first, then bulk-fetching brand and stock display data.

**Architecture:** Keep the existing `CatalogProductQueryDao` port and response shape. Replace the 4-table list query with a page query over `Product` plus `ProductStats`, then compose rows from `Brand` and `ProductStock` `IN` queries.

**Tech Stack:** Kotlin, Spring Boot, JPA/Hibernate, MySQL, k6.

---

### Task 1: Query Split Correctness And Shape

**Files:**
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/CatalogProductQueryDaoIntegrationTest.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/CatalogProductQueryDao.kt`

- [ ] **Step 1: Write the failing test**

Add a DAO integration test that clears Hibernate statistics immediately before the list query and expects three prepared statements: page query, brand bulk query, and stock bulk query.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.CatalogProductQueryDaoIntegrationTest"
```

Expected: the new query-shape test fails because the current DAO performs one 4-table select.

- [ ] **Step 3: Implement query split**

Change `findDisplayableProducts` and `findDisplayableProductsByBrandId` to:

1. Fetch a sorted page from `Product` joined only to `ProductStats`.
2. Fetch active brands by `brandId in (...)`.
3. Fetch stocks by `productId in (...)`.
4. Reassemble `CatalogInfo.ProductDisplayRow` in page order.

- [ ] **Step 4: Run GREEN**

Run the same DAO integration test and confirm it passes.

### Task 2: V2 Performance Report

**Files:**
- Use: `performance/k6/product-list-likes-desc.js`
- Use: `performance/compare-k6-latency.js`
- Create artifacts under: `performance/results/`

- [ ] **Step 1: Run after benchmark**

Run:

```bash
BASE_URL=http://localhost:18080 BRAND_ID=1001 k6 run --summary-export performance/results/product-list-v2-after.json performance/k6/product-list-likes-desc.js
```

- [ ] **Step 2: Compare**

Run:

```bash
node performance/compare-k6-latency.js performance/results/product-list-v2-before-warm.json performance/results/product-list-v2-after.json
```

- [ ] **Step 3: Verify**

Run focused tests and lint:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.CatalogProductQueryDaoIntegrationTest"
./gradlew ktlintCheck
```
