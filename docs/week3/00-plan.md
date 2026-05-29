# Week-3 도메인 구현 + DDD 학습 계획

> 산출물 보관 위치: 본 플랜이 승인되면 Phase 0의 첫 작업으로 본 문서를 `docs/week3/00-plan.md`로 옮긴 뒤 변경분을 commit.

---

## 1. Context

3주차 과제는 Brand / Product / Like / Order 네 도메인을 **DDD + Layered+DIP** 로 구현하는 것입니다. 코드만 짜는 게 아니라 사용자가 DDD를 **구현으로 학습**하는 흐름이 핵심이고, week-2에서 이미 도메인별 시나리오·도메인 모델·ERD·시퀀스가 합의되어 있어 본 주차는 그 설계를 코드로 옮기면서 결함이 보이면 docs도 같이 정정하는 사이클로 진행합니다. account 도메인은 이미 멀티모듈로 머지된 상태라 그대로 두고, 본 주차 신규 코드는 `apps/commerce-api` 단일 앱 안에서 패키지 계층으로 분리합니다. Inventory는 week-2 P-?4 결정대로 Product와 분리된 5번째 도메인으로 다룹니다.

## 2. 사용자 확정 결정 (재논의 금지)

1. 모듈 구조 — `apps/commerce-api` 단일 앱 / `com.loopers.{interfaces|application|domain|infrastructure}.{도메인}/` 패키지 분리.
2. Inventory — Product와 별도 5번째 도메인.
3. 구현 순서 — Brand → Product+Inventory → Like → Order.
4. DDD 학습 깊이 — phase 직전에 해당 개념만 발췌(공식 docs > 빅테크 블로그 > 개인 블로그).
5. 설계 정책 — `docs/week2/*`는 frozen contract 아님. 구현 중 결함 발견 시 코드 PR과 같은 PR에서 docs도 정정 (commit은 `feat` / `docs` 분리).

## 3. 목표 분리

### 3.1 학습 목표 (구현에 필요한 만큼만)
- §3.4 Entity vs Value Object
- §3.5 Aggregate & Aggregate Root
- §3.6 Domain Service vs Application Service
- §3.7 Repository pattern (DDD 원전 + 본 프로젝트 DIP 해석)
- §3.8 Ubiquitous Language (이미 작성된 사전 활용)
- §3.9 Domain Event (자리만 표시, 본 주차 미발행)

### 3.2 구현 목표 (도메인별 범위)

| 도메인 | 범위 | 비범위 (본 주차 명시 제외) |
|---|---|---|
| Brand | 사용자 단건 조회 + 관리자 CRUD | BrandHistory 비동기 적재, contactEmail(B-?1) |
| Product | 사용자 단건/목록(정렬 enum 화이트리스트) + 관리자 CRUD | ProductHistory, imageUrl(P-?1) |
| Inventory | Entity + Repository + Service 인터페이스 합의 | 동시성 메커니즘(O-?3, P-?4 detail) |
| Like | 토글(멱등) + 본인 목록 + product.like_count 비정규화 동기 증감 | LikeEvent 적재, 비동기 집계 |
| Order | 주문 생성(snapshot + Inventory 차감 협업) + 본인/관리자 조회 | 결제, 취소/환불, 멱등키, OrderEvent |

### 3.3 문서 목표
- CLAUDE.md에 §13 "도메인 & 객체 설계 전략" + §14 "아키텍처, 패키지 구성 전략" 추가.
- 본 플랜을 `docs/week3/00-plan.md`로 보관 + phase별 진행 노트는 `docs/week3/*` 하위에 차곡차곡.
- 구현 중 발견되는 docs 정정은 §7 동기화 정책에 따라 별도 commit.

---

## 3.4~3.9 DDD 학습 로드맵

학습 자료 우선순위: **공식 docs > 빅테크 엔지니어링 블로그 > 개인 블로그**. 각 개념은 phase 직전 30~60분 분량으로 읽고 본 프로젝트의 어디에 적용되는지 한 줄로 매핑.

### §3.4 Entity vs Value Object — Brand phase 직전
- 정의: 식별성(`id`)으로 동일성 → Entity. 속성 동일성 + immutable → VO.
- 본 프로젝트 적용: Brand/Product/ProductLike/Order/OrderItem/Inventory = Entity. BrandName/Price/Money/Quantity/LikeCount = `@Embeddable` VO + `init` invariant.
- 자료:
  - Microsoft .NET DDD: "Implement value objects" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/implement-value-objects
  - Microsoft .NET DDD: "Design a microservice domain model" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/microservice-domain-model
  - Eric Evans, *DDD* Ch.5 "A Model Expressed in Software"
  - Martin Fowler bliki, "ValueObject" — https://martinfowler.com/bliki/ValueObject.html

### §3.5 Aggregate & Aggregate Root — Order phase 직전
- 정의: 일관성 경계의 단위. 외부는 root를 통해서만 내부 entity에 접근. 트랜잭션 단위.
- 본 프로젝트 적용: multi-entity aggregate는 **Order(root) + OrderItem** 하나만. JPA `@OneToMany(cascade=ALL, orphanRemoval=true)` + `order_item.order_id`에 FK + `ON DELETE CASCADE`. 나머지는 single-entity aggregate.
- 자료:
  - Microsoft .NET DDD: "Design a DDD-oriented microservice / Aggregate" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice
  - Vaughn Vernon, *Implementing DDD* Ch.10 "Aggregates" (4 rules: by invariants / small / reference by id / eventual consistency outside)
  - Eric Evans, *DDD* Ch.6 "The Life Cycle of a Domain Object"
  - Martin Fowler bliki, "DDD_Aggregate" — https://martinfowler.com/bliki/DDD_Aggregate.html

### §3.6 Domain Service vs Application Service — Product+Inventory phase 직전
- 정의: 단일 entity로 표현 못 하는 도메인 행위 = Domain Service(stateless, domain layer). UC 조립 + 트랜잭션 + 인증 = Application Service(facade).
- 본 프로젝트 적용:
  - Domain Service — `ProductCatalogService`(Product + Brand + Inventory 합성), `OrderPlacementService`(snapshot + Inventory 차감 협업).
  - Application Service — `BrandFacade` / `ProductFacade` / `LikeFacade` / `OrderFacade` (request/response DTO 매핑 + 인증 + 트랜잭션).
  - 단순 단일 entity CRUD(Brand 생성)는 Application Service → Repository로 충분. Domain Service 강제 신설 금지 (YAGNI).
- 자료:
  - Microsoft .NET DDD: "Designing a DDD-oriented microservice" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice
  - Vaughn Vernon, *IDDD* Ch.7 "Services"
  - Martin Fowler bliki, "AnemicDomainModel" — https://martinfowler.com/bliki/AnemicDomainModel.html (anti-pattern 회피)

### §3.7 Repository Pattern + DIP 해석 — Brand phase 직전 (결정 확정용)
- 원전(Evans): Repository 인터페이스는 도메인 모델의 일부.
- 본 프로젝트 결정:
  - **account 모듈은 그대로** (Repository 인터페이스 in persistence module, layered 단순화).
  - **commerce-api 새 도메인은 과제 정책 채택**: 인터페이스 = `com.loopers.domain.{도메인}` / 구현체 = `com.loopers.infrastructure.{도메인}` / Spring Data JPA 인터페이스도 `com.loopers.infrastructure.{도메인}` 아래(JpaConfig의 `@EnableJpaRepositories(basePackages = ["com.loopers.infrastructure"])` 제약).
- 자료:
  - Microsoft .NET DDD: "Infrastructure persistence layer / Repository pattern" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/infrastructure-persistence-layer-design
  - Eric Evans, *DDD* Ch.6 "Repositories"

### §3.8 Ubiquitous Language — Phase 0 확인
- 본 프로젝트에서는 `docs/ubiquitous-language.md` (이미 작성됨)를 사전으로 활용. 새 코드에서:
  - 인증은 `account-security` 모듈 재사용 (commerce-api가 `account-application` / `account-persistence` / `account-security` 의존). 인증된 `accountId`가 `RequestAttribute("accountId")`로 주입되며, 이것이 commerce 도메인의 사용자 식별자다 — 별도 stub/매핑 없음(외부 userId == 내부 accountId). R4 stub 정책 폐기.
  - 약어 금지(§9): 풀네임만.
  - 시나리오 ID(S-C1, P-R4 등)를 `@DisplayName`에 그대로 인용 → 평가 trace.
- 자료:
  - DDD Reference (Evans 무료 PDF) "Ubiquitous Language" — https://www.domainlanguage.com/ddd/reference/
  - Eric Evans, *DDD* Ch.2

### §3.9 Domain Event (자리만, Wrap-up phase 짧게)
- 본 주차 미발행. 미래 도입 시 `applicationEventPublisher.publishEvent(...)` + `@TransactionalEventListener(phase = AFTER_COMMIT)` 위치는 week-2 시퀀스 다이어그램의 비동기 화살표 자리(B-F2 / P-F3 / L-F1 / O-F6).
- 자료:
  - Microsoft .NET DDD: "Domain events: design and implementation" — https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation
  - Spring docs, `@TransactionalEventListener` — https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html

---

## 4. Phase 구성

각 phase 끝에 `./gradlew :apps:commerce-api:test --tests '*<Domain>*'` + `ktlintCheck` 통과 + 해당 phase의 docs 동기화 + commit 분리.

### Phase 0 — Setup
**학습:** §3.7 (5분 확정값 재확인), §3.8 (10분 사전 재확인).

**작업:**
- 본 플랜 문서를 `docs/week3/00-plan.md`로 이동 + 초기 commit.
- `apps/commerce-api/src/test/kotlin/com/loopers/support/DatabaseCleanup.kt` — `apps/account-api`의 동일 파일 복사 + `@Profile("test")`.
- 인증 필터는 신설하지 않는다 — `account-security`의 `AccountHeaderAuthenticationFilter`(사용자) / `AdminLdapAuthenticationFilter`(관리자)를 재사용한다. 두 필터는 `accountService.authenticate`로 실인증 후 `RequestAttribute("accountId")`를 주입한다 (R4: stub 폐기 — commerce-api가 `account-application` / `account-persistence` / `account-security` 의존).
- `AccountSecurityConfig`(`@Configuration`, `com.loopers.account.security`)가 commerce-api component scan으로 자동 적용되어 SecurityFilterChain을 구성한다 → commerce-api에 별도 `SecurityConfig`를 신설하지 않는다 (SecurityFilterChain 빈 중복 회피).
- ExampleModel/Service/Facade/Controller는 이미 제거됨 (commit `0f86ceb`). 미러링 금지 원칙(`ApiResponse.success(it)` 직접 반환 + `ErrorType` deprecated, R5)은 그대로 유효.

**테스트:** 두 인증 필터 단위 테스트는 `account-security` 모듈에 이미 존재 (commerce-api 신규 불필요). commerce-api는 `DatabaseCleanup` smoke만 추가.

**docs 동기화:** 없음 (이 phase에서는 코드만).

**체크리스트 매핑:** 🧱 패키지 계층+도메인 골격.

### Phase 1 — Brand
**학습:** §3.4 (30~60분), §3.7 (재확인).

**패키지/파일:**
```
domain/brand/Brand.kt, BrandName.kt, BrandStatus.kt, BrandErrorCode.kt, BrandRepository.kt
infrastructure/brand/BrandJpaRepository.kt, BrandRepositoryImpl.kt
application/brand/BrandService.kt, BrandFacade.kt
interfaces/api/brand/BrandV1Controller.kt, AdminBrandV1Controller.kt, BrandV1Dto.kt, BrandV1ApiSpec.kt
```

**도메인 invariant:**
- `BrandName.init` 길이 1~50, blank 금지 → `BadRequestException(INVALID_BRAND_NAME)`.
- `markDeleted()` idempotent. ACTIVE → DELETED 단방향.

**Repository 인터페이스 (domain):** `save`, `findById`, `findAll(Pageable)`, `existsByName`.

**ErrorCode:** `BRAND_NOT_FOUND`(404), `DUPLICATE_BRAND_NAME`(409), `INVALID_BRAND_NAME`(400).

**테스트:** `BrandTest`, `BrandServiceTest`(mock), `BrandRepositoryIntegrationTest`(@DataJpaTest + H2), `BrandV1ControllerE2ETest` / `AdminBrandV1ControllerE2ETest` (@SpringBootTest + MockMvc + `DatabaseCleanup.@BeforeEach`, 시나리오 ID `@DisplayName`).

**docs 동기화 체크 (Phase 1에서 발생할 가능성):**
- 04-erd.md / 01-brand의 BaseEntity 4컬럼 표(`createdBy`/`updatedBy` 포함) ↔ 실제 BaseEntity 3컬럼 + `deletedAt`. **본 주차 결정: 코드 그대로(`createdBy`/`updatedBy` 미도입 — YAGNI).** docs 4컬럼 표기를 본 phase에서 정정.
- **`status: ACTIVE/DELETED` enum vs BaseEntity의 `deletedAt` 빌트인** — soft delete가 BaseEntity에 이미 있어 `status` enum이 사실상 중복. **본 주차 결정: status enum 제거, BaseEntity.`delete()` / `deletedAt`만 사용.** 응답 DTO에서는 `deletedAt != null` 여부로 판단. docs(01-brand §2, 02-product §2, 04-erd §status) 정정.

**체크리스트 매핑:** 🏷 일부 (브랜드 entity), 🧱 (Repository in domain).

### Phase 2 — Product + Inventory
**학습:** §3.6 (30~45분).

**Product 패키지:**
```
domain/product/Product.kt, Price.kt, LikeCount.kt, ProductStatus.kt 제거(R3 결정), ProductSort.kt, ProductErrorCode.kt, ProductRepository.kt, ProductCatalogService.kt(domain service)
infrastructure/product/ProductJpaRepository.kt, ProductRepositoryImpl.kt
application/product/ProductService.kt, ProductFacade.kt
interfaces/api/product/ProductV1Controller.kt, AdminProductV1Controller.kt, ProductV1Dto.kt, ProductV1ApiSpec.kt
```

**Inventory 패키지:**
```
domain/inventory/Inventory.kt, Quantity.kt, InventoryErrorCode.kt, InventoryRepository.kt
infrastructure/inventory/InventoryJpaRepository.kt, InventoryRepositoryImpl.kt
application/inventory/InventoryService.kt
```

**핵심 협업 위치 (학습 포인트):**

| 행위 | 위치 | 이유 |
|---|---|---|
| Brand 존재 검증 (Product 생성 시) | `ProductService.create` | 단순 검증, application 책임 |
| Inventory row 함께 생성 (cascade) | `ProductService.create` → `InventoryService.createFor` | cascade는 application 명시(conventions §1) |
| 응답 DTO에 Brand+Inventory 합성 | `ProductCatalogService` (**domain service**) | 단일 entity로 표현 불가한 합성 |
| `softDeleteByBrand(brandId)` | `ProductService` (application) | Brand → Product → Inventory 3단 cascade의 가운데 |
| 정렬 처리 (`ProductSort` → `Sort`) | `ProductService.list` | enum 매핑, 단순 |

**도메인 invariant:**
- `Price.init` value ≥ 0.
- `LikeCount.init` value ≥ 0, `increment()`, `decrement()`(value > 0일 때만 감소, 0 이하는 no-op).
- `Quantity.init` value ≥ 0. `decrease(n)` 부족 시 throw `ConflictException(STOCK_INSUFFICIENT)`. `increase(n)`.

**ErrorCode:**
- Product: `PRODUCT_NOT_FOUND`(404), `INVALID_PRODUCT_NAME`(400), `INVALID_PRICE`(400), `INVALID_PRODUCT_SORT`(400).
- Inventory: `INVENTORY_NOT_FOUND`(404), `STOCK_INSUFFICIENT`(409), `INVALID_QUANTITY`(400).
- ProductService는 brand 미존재 시 **`NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)` import해서 throw** (CLAUDE.md §13 — owning 도메인 enum 재사용).

**테스트:** `ProductTest`, `InventoryTest`, `QuantityTest`(decreaseBelowZero throws), `ProductServiceTest`(mock), `ProductCatalogServiceTest`(Brand/Inventory mock 합성 검증), `InventoryServiceTest`(decreaseAll 일괄, 일부 부족 시 전체 throw), `ProductRepositoryIntegrationTest`(정렬 3종 + brandId 필터 페이지네이션), `ProductV1ControllerE2ETest`, `AdminProductV1ControllerE2ETest`.

**docs 동기화:** Phase 1에서 status 제거 결정을 Product에도 적용. `02-product` final docs의 `imageUrl` 미구현 명시. ProductHistory `stock` 컬럼 미구현 명시.

**체크리스트 매핑:** 🏷 전체 (브랜드+좋아요수+정렬+재고+음수 방지), 🧩 (Product+Brand 합성 도메인 서비스), 🧱.

### Phase 3 — Like
**학습:** §3.6 재확인 5분 (LikeService는 application service — 단순 협업, domain service 불필요).

**패키지/파일:**
```
domain/like/ProductLike.kt, LikeErrorCode.kt, ProductLikeRepository.kt
infrastructure/like/ProductLikeJpaRepository.kt, ProductLikeRepositoryImpl.kt
application/like/LikeService.kt, LikeFacade.kt
interfaces/api/like/LikeV1Controller.kt, LikeV1Dto.kt, LikeV1ApiSpec.kt
```

**핵심 정책:**
- `ProductLike` UK = (`user_id`, `product_id`). BaseEntity의 `createdAt`만 의미 있고 `updatedAt` / `deletedAt`은 dead column (immutable + hard delete). 수용.
- 멱등: `like()` — UK 위반 catch → 200 swallow. `unlike()` — delete 결과 0 row → 204 idempotent (L-?1 / L-?2).
- `Product.like()` / `Product.unlike()` 도메인 메서드로 `like_count` 증감 (LikeService가 같은 트랜잭션에서 호출).
- 본인 검증: `LikeController.findMine(@PathVariable userId)` 에서 `RequestAttribute("accountId")`(인증된 사용자)와 비교, 불일치 → `ForbiddenException` (L-?5, 403). 외부 path의 `userId`는 내부 `accountId`와 동일 식별자.

**테스트:** `ProductTest.likeIncrementsCount` / `unlikeDecrementsCount` / `unlikeAtZeroIsNoOp`, `LikeServiceTest`(like/unlike 멱등 + 404 + Product mock으로 like_count 증감 검증), `ProductLikeRepositoryIntegrationTest`(UK 위반), `LikeV1ControllerE2ETest`(L-C1/L-C3/L-C4/L-D1/L-D3/L-R3).

**docs 동기화:** LikeEvent / 비동기 집계 본 주차 미구현 명시.

**체크리스트 매핑:** 👍 전체 (별도 도메인 + 상품 조회 시 likeCount + 등록/취소 단위 테스트 명시 충족).

### Phase 4 — Order
**학습:** §3.5 (45~60분), §3.6 재확인 5분.

**패키지/파일:**
```
domain/order/Order.kt(aggregate root), OrderItem.kt, Money.kt, OrderStatus.kt(CREATED 단일), OrderErrorCode.kt, OrderRepository.kt, OrderPlacementService.kt(domain service)
infrastructure/order/OrderJpaRepository.kt, OrderRepositoryImpl.kt
application/order/OrderService.kt, OrderFacade.kt
interfaces/api/order/OrderV1Controller.kt, AdminOrderV1Controller.kt, OrderV1Dto.kt, OrderV1ApiSpec.kt
```

**Aggregate 규칙 (학습 포인트 — 코드에 강제):**
- `@Table(name = "orders")` (MySQL 예약어, R6).
- `Order.items`는 외부 노출 시 `unmodifiableList`. 추가는 `Order.addItem(...)` 또는 factory를 통해서만.
- `Order.create(userId, snapshots): Order` factory가 invariant 검증: items 비어있지 않음(O-C6) / quantity ≥ 1(O-C5) / totalAmount = Σ(unitPrice × quantity)(O-C1).
- aggregate 밖에서 `OrderItem`을 id로 직접 참조 금지 — Order를 통해서만.

**Domain Service vs Application Service (백미):**
- `OrderPlacementService.place(userId, items): Order` (domain service, stateless)
  1. `productRepository.findAllByIdInAndDeletedAtIsNull(productIds)` → 누락 시 `NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)` (O-C3).
  2. snapshot 만들기 (productName, brandId, brandName, unitPrice).
  3. `inventoryService.decreaseAll(items)` 동기 호출 → 부족 시 `ConflictException(InventoryErrorCode.STOCK_INSUFFICIENT)` (O-C4) → 트랜잭션 롤백.
  4. `Order.create(userId, snapshots)` 반환.
- `OrderService.place(userId, command)` (application, `@Transactional`)
  - items 비었으면 400(O-C6), `OrderPlacementService.place` 호출, `orderRepository.save`, Info 반환.
- `OrderFacade.place(userId, request)` — Request → Command → Service → Response.

**본인 검증 (O-?7 → 404, Like와 다름):**
- `OrderService.findById(orderId, requesterUserId)` — userId 불일치 시 `NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)` (의도적 결정, ubiquitous-language §14).

**테스트:** `OrderTest`(빈 items throw, totalAmount 계산, addItem 재계산), `OrderPlacementServiceTest`(정상 / product 누락 → 404 / 재고 부족 → 409), `OrderServiceTest`(빈 items 400 / 본인 아닌 조회 404), `OrderRepositoryIntegrationTest`(findByUserIdAndOrderedAtBetween, cascade persist + orphanRemoval), `OrderV1ControllerE2ETest` (O-C1, O-C3, O-C4, O-C5, O-C6, O-R1, O-R2, O-R4, O-R5, O-R7).

**docs 동기화:** O-?3 동시성 미구현 주석. `OrderItem.brandId?` / `brandName?` nullable 유지 invariant 명시.

**체크리스트 매핑:** 🛒 전체, 🧩 (OrderPlacementService domain service + OrderService application service), 🧱.

### Phase 5 — Wrap-up
**학습:** §3.9 (15분).

**작업:**
1. CLAUDE.md §13 / §14 추가 (§5 참조).
2. docs/week2 누적 정정 (BaseEntity 4컬럼 → 3컬럼 + `deletedAt`, status enum 제거, 비구현 항목 섹션 등).
3. `./gradlew :apps:commerce-api:test ktlintCheck` 전체 통과 확인.
4. `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'` 기동, `http/commerce-api/*.http`로 4도메인 happy path smoke.
5. PR 본문에 과제 체크리스트 매핑표.

**체크리스트 매핑:** 🤖 (CLAUDE.md 확장).

---

## 5. CLAUDE.md 확장 항목

### §13 도메인 & 객체 설계 전략
- **약어 금지** — `docs/ubiquitous-language.md` §9에 위임. 코드/패키지/테스트/PR/주석 모두 풀네임.
- **Entity vs VO** — 식별성+라이프사이클 → Entity. 속성 동일성+immutable → `@Embeddable` VO + `init` invariant. 길이 상한 등 숫자는 `@Column.length`와 `init`에 **직접 숫자**(상수화 금지 — §4 일관).
- **Aggregate Root** — 외부는 root를 통해서만 내부 entity 접근. 본 프로젝트 multi-entity aggregate = `Order + OrderItem` 하나. 그 외는 single-entity. JPA cascade는 aggregate 안에서만, 도메인 간에는 DB FK / cascade 없음.
- **Domain Service vs Application Service** —

  | 행위 | 위치 |
  |---|---|
  | 단일 entity의 invariant / 상태 전이 | Entity 메서드 |
  | 여러 entity / aggregate 사이의 도메인 협업, stateless | Domain Service |
  | 트랜잭션 / 인증 / Request·Response DTO 매핑 / 외부 어댑터 호출 | Application Service (Facade) |
  | 단순 단일 entity CRUD | Application Service → Repository 직접. Domain Service 강제 신설 금지 (YAGNI) |

  Anemic Domain Model 회피 — entity 메서드로 표현 가능한 것을 service로 빼내는 충동 경계.
- **Domain Event (자리만)** — 본 주차 미발행. 미래 도입 시 `applicationEventPublisher.publishEvent(...)` + `@TransactionalEventListener(AFTER_COMMIT)` 자리는 week-2 시퀀스의 비동기 화살표(B-F2/P-F3/L-F1/O-F6).
- **ErrorCode 소유** — owning 도메인 enum에 정의. 다른 도메인이 throw할 때는 import해서 사용, 중복 정의 금지.

### §14 아키텍처, 패키지 구성 전략
- **commerce-api 단일 앱 / 패키지 계층 분리** — `apps/commerce-api/src/main/kotlin/com/loopers/{interfaces|application|domain|infrastructure}/{도메인}/`. account는 멀티모듈로 분리되어 있으나 본 앱은 단일 모듈 (YAGNI).
- **의존 방향** — `interfaces → application → domain ← infrastructure`. domain은 다른 어느 패키지도 의존하지 않는다. infrastructure는 domain 인터페이스를 구현한다 (DIP).
- **Repository 인터페이스 위치 (account와 의도적으로 갈림)**
  - account 모듈: persistence 모듈에 인터페이스 (그대로 유지).
  - commerce-api 새 도메인: 인터페이스 = `com.loopers.domain.{도메인}` / 구현체 = `com.loopers.infrastructure.{도메인}` / Spring Data JPA 인터페이스도 `com.loopers.infrastructure.{도메인}` 아래 (JpaConfig `basePackages=["com.loopers.infrastructure"]` 제약).
- **Cascade 정책** — 도메인 간 DB FK 없음 (soft reference, `docs/conventions.md` §1). cascade는 application 명시 호출 (`BrandService.delete` → `ProductService.softDeleteByBrand` → `InventoryService.archiveByProductIds`).
- **Snapshot 정책** — Order는 product/brand 정보를 OrderItem에 복사. product soft-delete 영향 없음.
- **인증/식별자 경계** — commerce-api는 `account-security`를 재사용해 실인증한다. 인증된 `accountId`가 `RequestAttribute("accountId")`로 주입되고 이것이 사용자 식별자다 (stub 없음, 외부 userId == 내부 accountId). 컨트롤러는 이 attribute를 읽어 본인 검증(Like 403 / Order 404)에 사용한다.
- **API 응답 래핑** — 컨트롤러는 도메인 DTO 또는 no body 반환. `ResponseBodyAdvice`가 `ApiResponse` 래핑. `ApiResponse.success(it)` 직접 반환 (Example*) deprecated, 미러링 금지.
- **에러 핸들링** — §2 일관. `ErrorType` deprecated. 새 도메인은 `CoreException` 서브클래스 + `{DOMAIN}:$name` ErrorCode enum.

---

## 6. 설계 ↔ 코드 동기화 정책

`docs/week2/*` SSOT but not frozen. 결함 발견 시 코드와 같은 PR에서 docs 정정, commit 분리.

**커밋 분리 룰:**
- `feat(brand): ...` — 코드.
- `docs(week2/brand): ...` — 같은 PR 별도 commit, 변경 사유 + 영향 산출물 1줄.

**docs 정정이 정당한 케이스:**
- 사전 정의가 코드 현실과 불일치 (BaseEntity 4컬럼 → 3컬럼 + `deletedAt`).
- 결정 카드가 본 주차 미구현으로 결론 (`contactEmail`, `imageUrl`, History 인프라).
- 새 어휘 도입 (`ProductCatalogService`, `OrderPlacementService`)을 ubiquitous-language §15 변경 이력에 추가.

**docs 정정이 금지되는 케이스:**
- 시나리오 ID(S-C1 등) 변경 — 평가 매핑 깨짐.
- 응답 status 결정(L-?5 403 / O-?7 404) 임의 변경.
- 외부 URL prefix (`/api/v1/`, `/api-admin/v1/`) 변경.

---

## 7. 검증 방법

**Phase 단위:**
- `./gradlew :apps:commerce-api:test --tests '*<Domain>*'` 통과.
- `./gradlew :apps:commerce-api:ktlintCheck` 통과.

**최종:**
- `./gradlew :apps:commerce-api:test` 전체 통과.
- `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'` 기동, `http/commerce-api/*.http` happy path smoke.

**체크리스트별 verification 매핑:**

| 항목 | 검증 산출물 |
|---|---|
| 🏷 브랜드 + likeCount 포함 | `ProductV1ControllerE2ETest.P-R1` 응답에 `brand` 객체 + `likeCount` |
| 🏷 정렬 latest/price_asc/likes_desc | `ProductV1ControllerE2ETest.P-R2.latest`, `.P-R4.priceAsc`, `.P-R4.likesDesc` |
| 🏷 재고 보유 + 음수 방지 도메인 레벨 | `InventoryTest` + `QuantityTest.decreaseBelowZeroThrows` |
| 👍 별도 도메인 / UK | `domain/like/` + `ProductLikeRepositoryIntegrationTest.uk_user_product` |
| 👍 조회 시 likeCount | `ProductV1ControllerE2ETest.P-R1` |
| 👍 등록/취소 단위 테스트 | `LikeServiceTest.like_idempotent` / `unlike_idempotent` |
| 🛒 다중 상품 + 재고 차감 | `OrderV1ControllerE2ETest.O-C1` + Inventory.quantity 감소 검증 |
| 🛒 4가지 예외 (유저/상품/재고/멱등성) | `OrderV1ControllerE2ETest.O-C3/O-C4/O-C5/O-C6` + 401 |
| 🛒 정상/예외 단위 테스트 | `OrderPlacementServiceTest.*`, `OrderServiceTest.*` |
| 🧩 Domain Service | `ProductCatalogService` / `OrderPlacementService` 존재 + 단위 테스트 |
| 🧩 stateless | 두 도메인 서비스에 `@Service` + 멤버 = final 의존성만 |
| 🧱 Repository in Domain Layer | `com.loopers.domain.{brand|product|inventory|like|order}.{X}Repository` 5개 |
| 🧱 패키지 = 계층+도메인 | tree 검증 |
| 🤖 CLAUDE.md 확장 | §13/§14 diff |

---

## 8. 리스크 & 미결정 항목

| ID | 항목 | 처리 |
|---|---|---|
| R1 | 재고 동시성 (O-?3, P-?4) | 본 주차 비범위. `InventoryService.decreaseAll`에 TODO 주석. |
| R2 | History 적재 인프라 (B-F2/P-F3/L-F1) | 본 주차 비범위. docs 시퀀스의 비동기 화살표 코드 미반영. |
| R3 | BaseEntity 4컬럼 vs 코드 3컬럼 + `status` enum 중복 | 코드 우선. Phase 1에서 status 제거 결정 + Phase 5에서 docs 전체 정정. |
| R4 | commerce-api 사용자 식별자 | **stub 폐기.** commerce-api가 `account-application` / `account-persistence` / `account-security`에 의존해 `account-security` 필터로 실인증. 인증된 `accountId`가 `RequestAttribute("accountId")`로 주입되어 사용자 식별자가 된다 (외부 userId == 내부 accountId). `AccountSecurityConfig`가 component scan으로 자동 적용 — commerce-api 신규 필터/SecurityConfig 불필요. |
| R5 | `ExampleV1Controller`의 `ApiResponse.success(...)` 직접 반환 | Deprecated, 미러링 금지. Example*는 sanity용으로 유지. |
| R6 | `orders` 테이블명 (MySQL 예약어) | `@Table(name = "orders")` 강제. |
| R7 | Brand → Product → Inventory 3단 cascade | 트랜잭션 1개. `BrandService.delete` → `ProductService.softDeleteByBrand` → 내부에서 `InventoryService.archiveByProductIds`. |
| R8 | LikeEvent / Brand·ProductHistory / OrderEvent 미구현 | docs 그대로, 코드는 발행 자리 주석. |

---

## Critical Files

- `apps/commerce-api/build.gradle.kts`
- `apps/commerce-api/src/main/kotlin/com/loopers/CommerceApiApplication.kt` (component scan 범위 확인)
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleModel.kt` (참조 패턴, 단 deprecated 부분 미러링 금지)
- `modules/jpa/src/main/kotlin/com/loopers/config/jpa/JpaConfig.kt` (Spring Data JPA basePackages 제약)
- `modules/persistence-core/src/main/kotlin/com/loopers/domain/BaseEntity.kt` (3컬럼 + `deletedAt` + `delete()` 빌트인)
- `apps/account-api/src/test/kotlin/com/loopers/support/DatabaseCleanup.kt` (복사 대상)
- `CLAUDE.md` (§13/§14 추가)
- `docs/ubiquitous-language.md` (§15 변경 이력 추가 대상)
- `docs/week2/01-requirements.md` + `docs/week2/{01-brand,02-product,03-likes,04-orders}/*.md` + `docs/week2/04-erd.md` (필요 시 정정)
