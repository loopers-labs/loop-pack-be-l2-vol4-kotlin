# Week-3 진행 체크리스트

> SSOT: [`00-plan.md`](./00-plan.md). 본 문서는 그 plan을 **실행 순서**대로 펼친 체크리스트입니다.
> 각 항목 끝나면 `[ ]` → `[x]`. 진행 중인 항목은 `[~]`로 표시.

---

## 진행 상태 요약

| Phase | 범위 | 상태 |
|---|---|---|
| Phase 0 | Setup (DatabaseCleanup, 헤더 필터 2종, SecurityConfig) | [ ] |
| Phase 1 | Brand | [ ] |
| Phase 2 | Product + Inventory | [ ] |
| Phase 3 | Like | [ ] |
| Phase 4 | Order (aggregate root) | [ ] |
| Phase 5 | Wrap-up (CLAUDE.md §13/§14 + docs 동기화) | [ ] |

---

## 시작 전 점검 (1회)

- [ ] 작업 브랜치 `assignment/week-03-domain` 위에 있는지 확인 (`git status`)
- [ ] `00-plan.md` §2 사용자 확정 결정 5개 머릿속 재확인 — **재논의 금지**
- [ ] `docs/ubiquitous-language.md` §1(행위자) / §9(약어 금지) 한 번 훑기
- [ ] `00-plan.md` §6 docs 정정 정책 — `feat` / `docs` commit 분리 룰 숙지

---

## Phase 0 — Setup

> 학습은 §3.7(5분) + §3.8(10분)로 최소. 코드는 신규 API 도메인이 의존할 토대만.

### 학습
- [ ] `00-plan.md` §3.7 Repository Pattern 결정값 재확인 — commerce-api 새 도메인은 인터페이스 in `com.loopers.domain.{도메인}`, 구현 in `com.loopers.infrastructure.{도메인}`
- [ ] `docs/ubiquitous-language.md` §1 행위자 호칭(사용자/로그인 사용자/관리자) + §9 약어 금지 표 재확인

### 코드
- [ ] `apps/commerce-api/src/test/kotlin/com/loopers/support/DatabaseCleanup.kt` — `apps/account-api`의 동일 파일 복사 (`@Component @Profile("test")`)
- [ ] `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/web/auth/UserHeaderAuthenticationFilter.kt` — `X-Loopers-LoginId` 존재 검증 + Long 파싱 → `RequestAttribute("userId")` 주입 (없으면 `UnauthorizedException`)
- [ ] `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/web/auth/AdminLdapAuthenticationFilter.kt` — `X-Loopers-Ldap == "loopers.admin"` 검증, `/api-admin/v1/**` 매칭
- [ ] `apps/commerce-api/src/main/kotlin/com/loopers/config/SecurityConfig.kt` — 두 필터 등록 + URL 매핑 (없으면 신설)

### 테스트
- [ ] `UserHeaderAuthenticationFilterTest` — 헤더 없음 → 401 / 정상 → attribute 주입 검증
- [ ] `AdminLdapAuthenticationFilterTest` — LDAP 미일치 → 403 / 일치 → 통과
- [ ] `DatabaseCleanup` smoke — `@BeforeEach`로 호출, 메타모델 기반 테이블 추출 동작 확인

### 검증 & commit
- [ ] `./gradlew :apps:commerce-api:test --tests '*AuthenticationFilter*'` 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] commit: `feat(commerce-api): X-Loopers-LoginId / Ldap 헤더 인증 필터 + DatabaseCleanup 추가`

### 미러링 금지 (Phase 0 함정)
- [ ] `ExampleV1Controller`의 `ApiResponse.success(it)` 직접 반환 패턴 복사 금지 (R5)
- [ ] `ErrorType` enum 신규 사용 금지 (deprecated)

---

## Phase 1 — Brand

> 학습: §3.4 Entity vs VO (30~60분), §3.7 재확인.

### 학습
- [ ] [Microsoft .NET DDD — Implement value objects](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/implement-value-objects) 1회독
- [ ] [Microsoft .NET DDD — Design a microservice domain model](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/microservice-domain-model) 1회독
- [ ] [Martin Fowler — ValueObject](https://martinfowler.com/bliki/ValueObject.html) 1회독
- [ ] 한 줄 매핑 정리: 본 프로젝트의 Entity 5종 vs VO 6종 (BrandName/Price/Money/Quantity/LikeCount + α)

### 도메인 코드
- [ ] `domain/brand/Brand.kt` — Entity, `BaseEntity` 상속. 생성자/factory, `markDeleted()` 도메인 메서드(단 BaseEntity.delete() 사용)
- [ ] `domain/brand/BrandName.kt` — `@Embeddable` VO, `init`에서 길이 1~50 + blank 금지 검증, 위반 시 `BadRequestException(INVALID_BRAND_NAME)`
- [ ] `domain/brand/BrandErrorCode.kt` — `BRAND_NOT_FOUND` / `DUPLICATE_BRAND_NAME` / `INVALID_BRAND_NAME`, prefix `BRAND:`
- [ ] `domain/brand/BrandRepository.kt` — 순수 Kotlin 인터페이스: `save`, `findById`, `findAll(Pageable)`, `existsByName`
- [ ] **금지**: `BrandStatus` enum 추가하지 말 것 (R3 — BaseEntity.deletedAt으로 충분)

### Infrastructure 코드
- [ ] `infrastructure/brand/BrandJpaRepository.kt` — Spring Data JPA 인터페이스 (`com.loopers.infrastructure` 아래 — JpaConfig basePackages 제약)
- [ ] `infrastructure/brand/BrandRepositoryImpl.kt` — `BrandRepository` 구현, `BrandJpaRepository` 위임

### Application 코드
- [ ] `application/brand/BrandService.kt` — `@Transactional` UC, command/info 클래스 동거
- [ ] `application/brand/BrandFacade.kt` — DTO 매핑, 인증 컨텍스트(Phase 0 attribute) 소비

### Interfaces 코드
- [ ] `interfaces/api/brand/BrandV1Dto.kt` — request/response DTO (응답에 `deletedAt`은 있는 그대로 노출, status 필드 없음)
- [ ] `interfaces/api/brand/BrandV1ApiSpec.kt` — OpenAPI 스펙 인터페이스
- [ ] `interfaces/api/brand/BrandV1Controller.kt` — `/api/v1/brands/{brandId}` 단건 조회
- [ ] `interfaces/api/brand/AdminBrandV1Controller.kt` — `/api-admin/v1/brands` CRUD

### 테스트
- [ ] `BrandTest` — invariant 단위 테스트 (BrandName 길이/blank)
- [ ] `BrandServiceTest` — Repository mock
- [ ] `BrandRepositoryIntegrationTest` — `@DataJpaTest` + H2, save/findById/existsByName
- [ ] `BrandV1ControllerE2ETest` — `@SpringBootTest + MockMvc + DatabaseCleanup`. `@DisplayName`에 시나리오 ID(`B-R1`, `B-C1` 등) 그대로 인용
- [ ] `AdminBrandV1ControllerE2ETest` — 관리자 CRUD + LDAP 헤더 누락 시 403

### docs 동기화 (Phase 1에서 발견되는 결함)
- [ ] `docs/week2/04-erd.md` — BaseEntity 4컬럼 표(`createdBy`/`updatedBy` 포함) → 3컬럼 + `deletedAt`으로 정정
- [ ] `docs/week2/01-brand/*` — `status: ACTIVE/DELETED` enum 제거, `deletedAt`으로 판단한다 명시
- [ ] commit 분리: `feat(brand): ...` + `docs(week2/brand): BaseEntity 정합화 + status enum 제거`

### 검증
- [ ] `./gradlew :apps:commerce-api:test --tests '*Brand*'` 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과

---

## Phase 2 — Product + Inventory

> 학습: §3.6 Domain Service vs Application Service (30~45분).

### 학습
- [ ] [Microsoft .NET DDD — Designing a DDD-oriented microservice](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice) 1회독
- [ ] [Martin Fowler — AnemicDomainModel](https://martinfowler.com/bliki/AnemicDomainModel.html) 1회독
- [ ] 한 줄 매핑 정리: 본 프로젝트의 Domain Service 2종 (`ProductCatalogService`, `OrderPlacementService`) vs Application Service 4종 (Facade 4개)

### Product — 도메인 코드
- [ ] `domain/product/Product.kt` — Entity, `like()` / `unlike()` 도메인 메서드 (LikeCount 증감)
- [ ] `domain/product/Price.kt` — `@Embeddable` VO, value ≥ 0
- [ ] `domain/product/LikeCount.kt` — `@Embeddable` VO, value ≥ 0, `increment()` / `decrement()`(0 이하 no-op)
- [ ] `domain/product/ProductSort.kt` — enum (LATEST / PRICE_ASC / LIKES_DESC), 화이트리스트
- [ ] `domain/product/ProductErrorCode.kt` — `PRODUCT_NOT_FOUND` / `INVALID_PRODUCT_NAME` / `INVALID_PRICE` / `INVALID_PRODUCT_SORT`, prefix `PRODUCT:`
- [ ] `domain/product/ProductRepository.kt` — 순수 Kotlin 인터페이스
- [ ] `domain/product/ProductCatalogService.kt` — **Domain Service** (Product + Brand + Inventory 합성)
- [ ] **금지**: `ProductStatus` enum (R3)

### Product — Infrastructure / Application / Interfaces
- [ ] `infrastructure/product/ProductJpaRepository.kt` + `ProductRepositoryImpl.kt`
- [ ] `application/product/ProductService.kt` — create 시 Brand 존재 검증 + `InventoryService.createFor` 호출 (cascade application 명시)
- [ ] `application/product/ProductFacade.kt`
- [ ] `interfaces/api/product/ProductV1Dto.kt` / `ProductV1ApiSpec.kt`
- [ ] `interfaces/api/product/ProductV1Controller.kt` — `/api/v1/products` 단건/목록 (정렬 enum)
- [ ] `interfaces/api/product/AdminProductV1Controller.kt`

### Inventory — 코드
- [ ] `domain/inventory/Inventory.kt` — Entity
- [ ] `domain/inventory/Quantity.kt` — `@Embeddable` VO, value ≥ 0, `decrease(n)` 부족 시 `ConflictException(STOCK_INSUFFICIENT)`, `increase(n)`
- [ ] `domain/inventory/InventoryErrorCode.kt` — `INVENTORY_NOT_FOUND` / `STOCK_INSUFFICIENT` / `INVALID_QUANTITY`
- [ ] `domain/inventory/InventoryRepository.kt`
- [ ] `infrastructure/inventory/InventoryJpaRepository.kt` + `InventoryRepositoryImpl.kt`
- [ ] `application/inventory/InventoryService.kt` — `decreaseAll(items)` 일괄 (한 건이라도 부족 시 전체 throw)
- [ ] **TODO 주석**: `decreaseAll`에 동시성 미구현(R1) 명시

### 협업 위치 검증 (학습 포인트)
- [ ] Brand 존재 검증은 `ProductService.create` (application 책임)
- [ ] Inventory row 동시 생성은 `ProductService.create` → `InventoryService.createFor`
- [ ] 응답 DTO Brand+Inventory 합성은 `ProductCatalogService` (domain service)
- [ ] `ProductService.softDeleteByBrand(brandId)` — Brand → Product → Inventory cascade의 가운데
- [ ] ProductService가 Brand 미존재 시 → `NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)` import 사용 (중복 정의 금지)

### 테스트
- [ ] `ProductTest` / `InventoryTest` / `QuantityTest.decreaseBelowZeroThrows`
- [ ] `ProductServiceTest` (Repository mock)
- [ ] `ProductCatalogServiceTest` (Brand/Inventory mock 합성)
- [ ] `InventoryServiceTest.decreaseAll_일부_부족이면_전체_throw`
- [ ] `ProductRepositoryIntegrationTest` — 정렬 3종 + brandId 필터 페이지네이션
- [ ] `ProductV1ControllerE2ETest` — `P-R1`(brand+likeCount 포함), `P-R2.latest`, `P-R4.priceAsc/likesDesc`
- [ ] `AdminProductV1ControllerE2ETest`

### docs 동기화
- [ ] `docs/week2/02-product/*` — `status` enum 제거 적용, `imageUrl` 미구현 명시, ProductHistory `stock` 컬럼 미구현 명시
- [ ] commit 분리: `feat(product): ...` / `feat(inventory): ...` / `docs(week2/product): ...`

### 검증
- [ ] `./gradlew :apps:commerce-api:test --tests '*Product*' --tests '*Inventory*'` 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과

---

## Phase 3 — Like

> 학습: §3.6 재확인 5분 (LikeService는 application — domain service 불필요).

### 도메인 코드
- [ ] `domain/like/ProductLike.kt` — Entity, UK `(user_id, product_id)`. createdAt만 의미, updatedAt/deletedAt dead column 수용
- [ ] `domain/like/LikeErrorCode.kt` — 필요 시 (대부분 다른 도메인 enum 재사용)
- [ ] `domain/like/ProductLikeRepository.kt` — 순수 Kotlin 인터페이스

### Infrastructure / Application / Interfaces
- [ ] `infrastructure/like/ProductLikeJpaRepository.kt` + `ProductLikeRepositoryImpl.kt`
- [ ] `application/like/LikeService.kt` — `@Transactional`. `like()`: UK 위반 catch → 멱등 200. `unlike()`: delete 결과 0 → 멱등 204. 같은 트랜잭션에서 `Product.like()` / `Product.unlike()` 호출해 `like_count` 비정규화 동기 증감
- [ ] `application/like/LikeFacade.kt`
- [ ] `interfaces/api/like/LikeV1Dto.kt` / `LikeV1ApiSpec.kt`
- [ ] `interfaces/api/like/LikeV1Controller.kt`
  - [ ] `findMine(@PathVariable userId)` — `RequestAttribute("userId")` 와 비교, 불일치 → `ForbiddenException` (L-?5, 403)

### 테스트
- [ ] `ProductTest.likeIncrementsCount` / `unlikeDecrementsCount` / `unlikeAtZeroIsNoOp`
- [ ] `LikeServiceTest` — like 멱등 / unlike 멱등 / Product mock으로 like_count 증감 검증 / 미존재 product 404
- [ ] `ProductLikeRepositoryIntegrationTest` — UK 위반 시 `DataIntegrityViolationException`
- [ ] `LikeV1ControllerE2ETest` — `L-C1`, `L-C3`, `L-C4`, `L-D1`, `L-D3`, `L-R3`

### docs 동기화
- [ ] `docs/week2/03-likes/*` — LikeEvent / 비동기 집계 본 주차 미구현 명시
- [ ] commit 분리: `feat(like): ...` / `docs(week2/likes): ...`

### 검증
- [ ] `./gradlew :apps:commerce-api:test --tests '*Like*'` 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과

---

## Phase 4 — Order (aggregate root, 백미)

> 학습: §3.5 Aggregate & Aggregate Root (45~60분), §3.6 재확인 5분.

### 학습
- [ ] [Microsoft .NET DDD — DDD-oriented microservice / Aggregate](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/ddd-oriented-microservice) 1회독
- [ ] [Martin Fowler — DDD_Aggregate](https://martinfowler.com/bliki/DDD_Aggregate.html) 1회독
- [ ] Vaughn Vernon 4 rules 정리: by invariants / small / reference by id / eventual consistency outside

### 도메인 코드
- [ ] `domain/order/Order.kt` — **Aggregate Root**, `@Table(name = "orders")` (MySQL 예약어, R6). `items`는 외부 노출 시 `unmodifiableList`. `Order.create(userId, snapshots): Order` factory가 invariant 검증 (items 비어있지 않음 O-C6 / quantity ≥ 1 O-C5 / totalAmount = Σ(unitPrice × quantity) O-C1)
- [ ] `domain/order/OrderItem.kt` — aggregate 내부 entity. `brandId?` / `brandName?` nullable 유지 (snapshot 한계). FK + `ON DELETE CASCADE` + JPA `@OneToMany(cascade=ALL, orphanRemoval=true)`
- [ ] `domain/order/Money.kt` — `@Embeddable` VO
- [ ] `domain/order/OrderStatus.kt` — `CREATED` 단일 (본 주차 결제 비범위)
- [ ] `domain/order/OrderErrorCode.kt` — `ORDER_NOT_FOUND` / `EMPTY_ITEMS` / `INVALID_QUANTITY` 등, prefix `ORDER:`
- [ ] `domain/order/OrderRepository.kt`
- [ ] `domain/order/OrderPlacementService.kt` — **Domain Service**

### Aggregate 규칙 코드 강제 (학습 포인트)
- [ ] aggregate 밖에서 `OrderItem`을 id로 직접 참조하는 코드 없음 (Repository도 OrderItemRepository 없음)
- [ ] `Order.addItem(...)` 또는 factory를 통해서만 OrderItem 추가
- [ ] `Order.totalAmount`는 도메인 계산 (factory + addItem에서 재계산)

### Infrastructure / Application / Interfaces
- [ ] `infrastructure/order/OrderJpaRepository.kt` + `OrderRepositoryImpl.kt`
- [ ] `application/order/OrderService.kt`
  - [ ] `place(userId, command)` — `@Transactional`. items 비었으면 400(O-C6), `OrderPlacementService.place` 호출, `orderRepository.save`, Info 반환
  - [ ] `findById(orderId, requesterUserId)` — userId 불일치 시 `NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)` (O-?7, 의도적 404)
- [ ] `application/order/OrderFacade.kt`
- [ ] `interfaces/api/order/OrderV1Dto.kt` / `OrderV1ApiSpec.kt`
- [ ] `interfaces/api/order/OrderV1Controller.kt`
- [ ] `interfaces/api/order/AdminOrderV1Controller.kt`

### Domain Service 흐름 검증
- [ ] `OrderPlacementService.place(userId, items): Order`
  - [ ] product 조회 → 누락 시 `NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)` (O-C3)
  - [ ] snapshot 생성 (productName, brandId, brandName, unitPrice)
  - [ ] `inventoryService.decreaseAll(items)` 동기 호출 → 부족 시 `ConflictException(InventoryErrorCode.STOCK_INSUFFICIENT)` (O-C4) → 트랜잭션 롤백
  - [ ] `Order.create(userId, snapshots)` 반환 (stateless, 의존성 모두 final)

### 테스트
- [ ] `OrderTest` — 빈 items throw, totalAmount 계산, addItem 재계산
- [ ] `OrderPlacementServiceTest` — 정상 / product 누락 404 / 재고 부족 409
- [ ] `OrderServiceTest` — 빈 items 400 / 본인 아닌 조회 404 (O-?7)
- [ ] `OrderRepositoryIntegrationTest` — findByUserIdAndOrderedAtBetween, cascade persist + orphanRemoval
- [ ] `OrderV1ControllerE2ETest` — `O-C1`, `O-C3`, `O-C4`, `O-C5`, `O-C6`, `O-R1`, `O-R2`, `O-R4`, `O-R5`, `O-R7`

### docs 동기화
- [ ] `docs/week2/04-orders/*` — O-?3 동시성 미구현 주석, `OrderItem.brandId?` / `brandName?` nullable invariant 명시
- [ ] commit 분리: `feat(order): ...` / `docs(week2/orders): ...`

### 검증
- [ ] `./gradlew :apps:commerce-api:test --tests '*Order*'` 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과

---

## Phase 5 — Wrap-up

> 학습: §3.9 Domain Event (15분, 자리만).

### CLAUDE.md 확장
- [ ] §13 "도메인 & 객체 설계 전략" 추가 (`00-plan.md` §5 참조)
  - 약어 금지 / Entity vs VO / Aggregate Root / Domain Service vs Application Service 표 / Domain Event 자리만 / ErrorCode 소유
- [ ] §14 "아키텍처, 패키지 구성 전략" 추가
  - commerce-api 단일 앱 / 의존 방향 / Repository 인터페이스 위치(account와 의도적으로 갈림) / Cascade 정책 / Snapshot / 인증 식별자 경계 / 응답 래핑 / 에러 핸들링

### docs 누적 정정 마무리
- [ ] `docs/week2/04-erd.md` 전체 BaseEntity 4컬럼 → 3컬럼 + `deletedAt` 통일 확인
- [ ] `docs/week2/*/` status enum 제거 누락 없는지 grep
- [ ] `docs/ubiquitous-language.md` §15 변경 이력에 신규 어휘 추가 (`ProductCatalogService`, `OrderPlacementService`)
- [ ] `docs/week3/00-plan.md` 결정 변경 사항이 있었으면 본문 inline 정정 (frozen 아님)

### 최종 검증
- [ ] `./gradlew :apps:commerce-api:test` 전체 통과
- [ ] `./gradlew :apps:commerce-api:ktlintCheck` 통과
- [ ] `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'` 기동
- [ ] `http/commerce-api/*.http` 4도메인 happy path smoke (Brand 조회 / Product 목록 / Like 토글 / Order 생성+조회)

### PR
- [ ] PR 본문에 과제 체크리스트(`00-plan.md` §7 verification 매핑표) 인용 + 시나리오 ID → 테스트 메서드 매핑
- [ ] commit 메시지 / PR 본문에 AI 흔적 (Co-Authored-By Claude 등) 없는지 재확인 (메모리: `feedback_no_ai_traces_in_commits`)

---

## 진행 시 영구 원칙 (매 phase 적용)

- [ ] 새 도메인 에러는 owning 도메인 enum에 정의, 다른 도메인은 import해서 사용 (중복 정의 금지) — CLAUDE.md §2
- [ ] 비즈니스/애플리케이션 실패는 `CoreException` 서브클래스만 사용 (`BadRequest`/`Unauthorized`/`Forbidden`/`NotFound`/`Conflict`/`InternalServer`) — `RuntimeException` ad hoc 금지
- [ ] 도메인 throw 시 `customMessage`에 사용자 입력값(이메일/loginId 등) 끼워넣지 않음 (PII 누수 회피) — CLAUDE.md §4
- [ ] `@Embeddable` VO 길이 상한은 `@Column.length` + `init` 양쪽에 **직접 숫자** (상수화 금지) — CLAUDE.md §4
- [ ] `@SpringBootTest`에 `@Transactional` 일괄 부착 금지. `DatabaseCleanup` + `@BeforeEach` 사용 — CLAUDE.md §6
- [ ] 약어 클래스/컴포넌트명 금지 (`Ctrl`/`Svc`/`Repo`/`Mgr` 등). 시퀀스 다이어그램 participant도 풀네임 — `docs/ubiquitous-language.md` §9
- [ ] 행위자는 "사용자 / 로그인 사용자 / 관리자" — "대고객" / "어드민" 금지
- [ ] 단일 구현체용 인터페이스 만들지 않음 — CLAUDE.md §3
