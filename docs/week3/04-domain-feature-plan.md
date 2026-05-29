# Week-3 도메인 구현 — 기능 플랜 (도메인별 세션 / 워크트리)

> **이 문서가 실제 과제 범위의 운영 플랜입니다.**
> `00-plan.md` / `01-checklist.md`의 `interfaces` / Controller / E2E / Admin / 웹 DTO / 인증 필터 / HTTP status 항목은
> **본 과제 범위 밖**이므로 무시합니다(아래 §0 참조). 도메인 설계 근거는 `02-ddd-concepts.html` / `03-architecture.html`,
> 구현 규칙은 `.claude/skills/ddd-layered-tdd` 스킬을 따릅니다.

---

## 0. 과제 목표 & 범위

**목표** (과제 원문)
- Product / Brand / Like / Order 핵심 개념을 **Entity · Value Object · Domain Service**로 모델링.
- **레이어드 + DIP**로 유연하고 테스트 가능한 구조.
- **Application Layer는 경량** — 도메인 객체를 조합하는 흐름을 실제 구현.
- **단위 테스트**로 도메인 로직의 정합성·규칙·예외/경계 검증.

| | IN (구현) | OUT (이번 과제 아님) |
|---|---|---|
| domain | Entity, VO(invariant), Domain Service, Repository **인터페이스**, ErrorCode | — |
| application | **경량 orchestration** (도메인 객체 조합 흐름) | — |
| infrastructure | Repository **구현체** + JPA (DIP) | — |
| interfaces | — | **Controller, REST 엔드포인트, `/api/v1`·`/api-admin/v1`, 웹 DTO(`{D}V1Dto`), ApiSpec** |
| 인증 | — | **필터, 401, 관리자/사용자 인증 헤더** |
| 테스트 | **단위 테스트**(Fake/Stub로 외부 의존 분리) + 예외/경계 | E2E(@SpringBootTest+MockMvc), 시나리오 ID @DisplayName |
| 예외 | 도메인/앱이 던지는 `NotFound`/`Conflict`/`Forbidden`/`BadRequest` (단위테스트로 검증) | **HTTP status 매핑**(web 책임) |

**비범위 (만들지 않음)**: 결제·취소·환불·멱등키·쿠폰 / history(BrandHistory·ProductHistory·LikeEvent) / Brand staff Role / 행동기반 랭킹·추천 / `contactEmail`·`imageUrl` / Inventory 동시성 메커니즘(인터페이스만) / status enum(=`deletedAt`로 대체) / `createdBy`·`updatedBy`(BaseEntity 3컬럼+`deletedAt`만).

---

## 1. 진행 방식 (브랜치 · PR · 세션)

- 베이스 브랜치: **`assignment/week-03-domain`** (= "week3").
- 도메인별로 week3에서 브랜치를 따서 작업 → **week3로 PR** → 사용자가 확인·merge → 다음 작업은 **머지된 week3에서 다시 따서 시작**.
- 브랜치 네이밍: `assignment/week-03-{domain}` (예: `assignment/week-03-brand`).
- **Claude 세션 5개 = 도메인 5개.** 각 세션은 시작 시 `ddd-layered-tdd` 스킬 + 본 문서를 연다.

### 세션 ↔ 도메인 ↔ 베이스

| 세션 | 도메인 | 브랜치 base | 선행 머지 필요 |
|---|---|---|---|
| 1 | **Brand** | week3 | 없음 (독립) |
| 2 | **Inventory** | week3 | 없음 (독립) — 세션 1과 병렬 가능 |
| 3 | **Product** | week3 (Brand+Inventory 머지 후) | Brand, Inventory |
| 4 | **Like** | week3 (Product 머지 후) | Product |
| 5 | **Order** | week3 (Product+Inventory 머지 후) | Product, Inventory |

### 의존 / 머지 순서

```
독립(병렬):   Brand        Inventory
                 └──────┬──────┘
                  [week3 머지]
                        ▼
                    Product        (Brand 존재검증 + Inventory 연동)
                  [week3 머지]
                        ▼
                ┌───────┴───────┐
                ▼               ▼
              Like            Order   (Product 머지 후 병렬 가능. Order는 Inventory도 필요)
```

- **완전 병렬 가능한 건 Brand·Inventory 둘뿐.** Product/Like/Order는 선행 도메인이 week3에 머지된 뒤 따야 컴파일됨(단일 모듈, 실제 entity/repo 의존).

---

## 2. 공통 주의사항 (모든 세션 적용)

1. **API/인증 만들지 않는다.** Controller·웹 DTO·HTTP status·필터·`/api/...` 경로 전부 제외. (§0)
2. **레이어 의존 방향(불변)**: `application → domain ← infrastructure`. **domain은 application/infrastructure를 import 하지 않는다**(DIP·모듈화 대비).
3. **Repository**: 인터페이스 = `com.loopers.domain.{도메인}` / 구현체·Spring Data JPA = `com.loopers.infrastructure.{도메인}` (JpaConfig `@EnableJpaRepositories(["com.loopers.infrastructure"])` 제약). 별도 PersistenceConfig 불필요.
4. **application service끼리 호출 금지.** cross-domain은 ① 읽기 가드는 다른 도메인 **Repository 포트로 인라인 read**, ② 도메인 규칙 있는 협업은 **Domain Service**(다른 도메인의 Entity·포트·도메인 메서드만, app service 호출 ❌).
5. **본인 검증은 service의 `requesterId: Long` 파라미터로** (인증 컨텍스트는 파라미터로 추상화 — 헤더/필터 없음). Like=`ForbiddenException`, Order=`NotFoundException`.
6. **soft delete는 `deletedAt`만**(BaseEntity 빌트인). `status` enum 만들지 않음.
7. **VO**: `@Embeddable` + `init`에서 길이+형식 검증(fail-fast). 컬럼 길이 상한은 `@Column.length`·`init`에 **직접 숫자**(상수화 금지). `equals`/`hashCode`/`toString`(원문) 구현.
   - **VO 총 3개: `BrandName` · `ProductName` · `Money`** (Money는 `domain/shared` shared kernel — Product 가격·Order 금액 공용. `Price` VO 없음).
   - **`LikeCount`·`Quantity`는 VO 아님** — 가변 카운터라 엔티티 필드 + 도메인 메서드(`Product.likeCount` + `like()/unlike()`, `Inventory.quantity` + `decrease()/increase()`). VO는 불변 값 개념에만.
8. **예외**: `CoreException` 서브클래스만(`BadRequest`/`Unauthorized`/`Forbidden`/`NotFound`/`Conflict`/`InternalServer`). 도메인 에러코드는 owning 도메인 enum(`{DOMAIN}:$name`), 타 도메인은 import 재사용(중복 정의 금지). `customMessage`에 PII 금지.
9. **TDD**: `ddd-layered-tdd` §7 순서 — 테스트 리스트 작성 → **사용자 confirm** → red → green → refactor. 케이스 설계는 `kent-beck-tdd`.
10. **단위 테스트**: Repository는 Fake/Stub로 분리해 service를 순수 단위로. 도메인(Entity/VO/Domain Service)은 Spring 없이. 예외·경계 케이스 포함.
11. **약어 클래스명 금지**(풀네임만), 사용자 응대는 **존댓말**.
12. PR/커밋에 **AI 흔적(Co-Authored-By 등) 금지**.
13. **목록 조회 = cursor 기반 keyset 페이지네이션**.
    - **공통 구현물은 `CursorPage<T>` 단 하나** — `com.loopers.domain.shared`(Money 옆), pure Kotlin. 최소 형태 `data class CursorPage<T>(val content: List<T>, val hasNext: Boolean)`. **없으면 Session 1(Brand 목록)에서 최초 구현**, 이후 다른 목록에서 그대로 재사용. opaque base64 커서 인코딩은 **컨트롤러 책임 = 이번 주 범위 밖** → `nextCursor` 필드는 지금 만들지 않음(다음 커서는 `content.last()` 키셋 값에서 presentation이 파생).
    - **`CursorPage<T>` 재사용 대상 (cursor 페이지네이션 적용):**

      | 세션 | 기능 | 정렬/필터 | `CursorPage<T>` |
      |---|---|---|---|
      | 1 | Brand 목록 조회 | `id DESC` | **최초 구현** |
      | 3 | Product 목록 조회 | `latest`/`price_asc`/`likes_desc` + `brandId` | 재사용 |
      | 4 | Like 본인 좋아요 목록 | `id DESC`(본인 것) | 재사용 |

    - **명시적 제외**: Order 본인 주문 목록(Session 5)은 **날짜범위(`startAt`/`endAt`) 필터** 방식 — cursor 페이지네이션 아님(필요 시 그 세션에서 별도 논의). 단건 조회는 페이지네이션 무관.
    - **공통화하지 않는 것(쿼리별)**: 커서 요청 파라미터(`lastId` / `(lastPrice,lastId)` / `(lastLikes,lastId)`), 정렬 enum(`ProductSort`, Product 전용), `Window<T>→CursorPage<T>` 매핑(각 infra 어댑터에서 한 줄).
    - **요청/커서는 공통화 금지** — 정렬마다 커서 형태가 다름. `latest`는 단일 `lastId`로 충분하나 `price_asc`/`likes_desc`는 **비유니크 → 복합키 `(sortValue, id)`** 필요. 단일-id 공통 요청 클래스는 가격/좋아요 정렬에서 깨짐. **쿼리별 명시 타입 파라미터**로 받는다.
    - **정렬 키 규칙** — 비유니크 1차 키는 **항상 `id DESC`(최신순)로 타이브레이크**. `latest` = `id DESC`(단일) / `price_asc` = `price ASC, id DESC` / `likes_desc` = `likeCount DESC, id DESC`. id가 유니크 total-order라 keyset이 중복·누락 없이 동작. 방향 혼합(`price ASC` + `id DESC`)도 `Sort`에 컬럼별 방향만 주면 `ScrollPosition.keyset()`이 predicate 생성.
    - **WHERE/커서 SQL을 손으로 짜지 않는다** — infrastructure 어댑터에서 **Spring Data keyset `ScrollPosition.keyset()` + `Window<T>`**(Boot 3.4) 사용. 복합키 WHERE·정렬을 프레임워크가 생성.
    - **레이어 경계**: `Window`/`ScrollPosition`은 **infrastructure에만**. 도메인 repository 포트는 `CursorPage<T>` 반환, 어댑터에서 `Window<Entity>` → `CursorPage`(`window.content` + `window.hasNext()`) 매핑.

---

## 3. 도메인별 기능 리스트

> 각 도메인 안에서 **다른 도메인에 의존하지 않는 기능(`[독립]`)을 먼저**, cross-domain 의존 기능(`[의존: X]`)을 뒤에 정렬했습니다.
> 세션은 위에서 아래 순으로 TDD 진행하면 의존이 자연스럽게 풀립니다.

### 세션 1 — 🏷 Brand  (의존: 없음)

패키지: `domain/brand` (Brand, BrandName, BrandRepository, BrandErrorCode) · `application/brand` (BrandService) · `infrastructure/brand` (BrandJpaRepository, BrandRepositoryImpl)

1. **브랜드 이름 VO 검증** `[독립]` — `BrandName` 1~50자·blank 금지 → `BadRequestException`
2. **브랜드 삭제 (soft)** `[독립]` — `deletedAt`, `delete()` 멱등
3. **브랜드 등록** `[독립]` — name 유일성 위반 시 `ConflictException`
4. **브랜드 단건 조회** `[독립]` — 없으면 `NotFoundException`
5. **브랜드 목록 조회** `[독립]` — pagination
6. **브랜드 수정** `[독립]` — name 유일성 재검증, id 불변
7. *(보류)* **삭제 cascade → Product** `[의존: Product]` — Brand 단독 세션 범위 밖. Product 머지 후 application에서 연결(optional)

단위 테스트 포인트: BrandName 경계(0/1/50/51자, blank), 유일성 충돌, soft delete 멱등, 조회 404.

### 세션 2 — 📦 Inventory  (의존: 없음 — productId는 Long으로만 참조)

패키지: `domain/inventory` (Inventory, InventoryRepository, InventoryErrorCode) · `application/inventory` (InventoryService) · `infrastructure/inventory`
> `quantity`는 **엔티티 필드(`Long`) + 도메인 메서드**. Quantity VO 없음(가변 카운터).

1. **재고 음수 방지 (도메인 메서드)** `[독립]` — `Inventory.decrease(n)` 부족 시 `ConflictException(STOCK_INSUFFICIENT)`, `increase(n)`. `quantity: Long`(≥ 0)은 엔티티 필드, 음수 방지는 엔티티 메서드 책임
2. **재고 생성** `[독립]` — `createFor(productId, quantity)`
3. **재고 증가** `[독립]` — `increase`
4. **재고 일괄 차감** `[독립]` — `decreaseAll(items)`: 하나라도 부족하면 전체 throw (동시성은 인터페이스만 — TODO 주석)
5. **재고 조회** `[독립]` — `by productId`

단위 테스트 포인트: `decrease` 경계(잔량=요청·잔량<요청 throw·0), `decreaseAll` 일부 부족 시 전체 실패, createFor/increase.

### 세션 3 — 🏷 Product  (의존: Brand, Inventory)

패키지: `domain/product` (Product, **ProductName**(VO), ProductSort, ProductRepository, ProductErrorCode, **ProductCatalogService**(domain service)) · `domain/shared` (**Money** VO — 본 세션에서 생성, Order가 재사용) · `application/product` (ProductService) · `infrastructure/product`
> `likeCount`는 **엔티티 필드(`Long`) + `like()`/`unlike()`**. LikeCount VO 없음. 가격은 `Money`(shared), `Price` VO 없음.

1. **상품명·가격 VO** `[독립]` — `ProductName` 1~100자·blank 금지, `Money` ≥ 0 (shared 화폐 VO, `domain/shared`)
2. **좋아요 수 증감 도메인 메서드** `[독립]` — `Product.likeCount: Long` 필드, `like()` 증가 / `unlike()` 감소(0 floor no-op). VO 아님
3. **상품 목록 조회** `[독립]` — 정렬 enum(`latest`·`price_asc`·`likes_desc`) + `brandId` 필터 + pagination (정렬은 화이트리스트, 잘못된 값 거부)
4. **상품 수정** `[독립]` — `brandId` 불변, name/price 변경
5. **상품 삭제 (soft)** `[독립]` — Product 자체 `deletedAt`
6. **상품 등록** `[의존: Brand, Inventory]` — `brandId` 존재 검증(`NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)`, 다른 도메인 Repository 포트 read), `Price` ≥ 0, name 1~100자, 생성 시 Inventory `createFor` 호출(application cascade)
7. **상품 상세 조회 (Product + Brand + likeCount 조합)** `[의존: Brand]` — **Domain Service `ProductCatalogService`** 가 조합, application이 위임. 반환 = 조합 Info(예: `ProductDetailInfo`)
8. **상품 삭제 cascade → Inventory** `[의존: Inventory]` — `ProductService`가 Inventory archive 호출(application)

단위 테스트 포인트: ProductName/Money 경계, like/unlike 증감·0 floor no-op, 정렬 3종·brandId 필터(Repository 통합 or Fake), 등록 시 brand 미존재 404, 상세 조합(Brand/Inventory mock).

### 세션 4 — 👍 Like  (의존: Product)

패키지: `domain/like` (ProductLike, ProductLikeRepository, LikeErrorCode) · `application/like` (LikeService) · `infrastructure/like`

1. **본인 좋아요 목록 조회** `[독립]` — `requesterId` 본인 검증(본인 아니면 `ForbiddenException`). 반환은 본인의 ProductLike 목록(또는 productId 목록)
2. **좋아요 등록** `[의존: Product]` — product 존재 검증(`NotFoundException`), UK `(userId, productId)` 멱등(중복 시 no-op), `Product.like()` 동기 호출로 `like_count` 증가
3. **좋아요 취소** `[의존: Product]` — 없으면 멱등 no-op(hard delete), `Product.unlike()` 동기 호출로 `like_count` 감소

단위 테스트 포인트: like 멱등·unlike 멱등, 미존재 product 404, 본인 아님 403, Product mock으로 like_count 증감 검증, UK 위반(Repository 통합 시).

### 세션 5 — 🛒 Order  (의존: Product, Inventory)

패키지: `domain/order` (Order(aggregate root), OrderItem, OrderStatus(CREATED), OrderRepository, OrderErrorCode, **OrderPlacementService**(domain service)) · `application/order` (OrderService) · `infrastructure/order`
> 화폐는 `domain/shared/Money`(Product 세션에서 생성) **재사용** — `totalAmount`/`unitPrice`가 `Money`. Order 도메인은 Product를 의존하지 않고 `domain.shared`만 의존.

1. **주문 aggregate invariant** `[독립]` — `Order.create(userId, snapshots)` factory: items ≥ 1(빈 → 예외), quantity ≥ 1, `totalAmount = Σ(unitPrice×quantity)`. `items`는 외부 노출 시 unmodifiable, 추가는 `addItem`/factory로만(재계산)
2. **본인 주문 단건 조회** `[독립]` — `requesterId` 불일치 시 `NotFoundException`(O-R4)
3. **본인 주문 목록 조회** `[독립]` — 날짜 범위(`startAt`/`endAt`)
4. **주문 생성** `[의존: Product, Inventory]` — **Domain Service `OrderPlacementService`**: ① Product 조회(없으면 `NotFoundException`) → ② snapshot 생성(productName/brandName/unitPrice) → ③ Inventory 차감(`InventoryRepository`/`inventory.decrease()`, 부족 시 `ConflictException`) → ④ `Order.create` 반환. `OrderService`(application)가 `@Transactional`로 감싸고 save
5. *(optional)* **전체 주문 조회** `[독립]` — 관리자 개념은 API/인증이라 단순 `findAll` 정도

단위 테스트 포인트: 빈 items·quantity<1·totalAmount 계산, OrderPlacementService 정상/상품부재(404)/재고부족(409), 본인 아닌 조회 404, cascade persist + orphanRemoval(Repository 통합 시).

> ⚠️ Order 핵심 규칙: `OrderPlacementService`(domain)는 **`InventoryService`(application)를 호출하지 않는다.** `InventoryRepository`(포트) 또는 `inventory.decrease()`(도메인 메서드)만 사용 — domain→application 역전 금지.

---

## 4. 도메인 간 협력 요약

| 협력 | 위치 | 방식 |
|---|---|---|
| 상품 등록 시 brand 존재 검증 | `ProductService`(app) | `BrandRepository` 포트로 read 가드 |
| 상품 등록 시 Inventory 생성 | `ProductService`(app) | `InventoryService.createFor`(application cascade) |
| 상품 상세 = Product+Brand+likeCount | `ProductCatalogService`(domain svc) | 도메인 객체·포트 조합 |
| 좋아요 시 like_count 증감 | `LikeService`(app) → `Product.like()/unlike()` | 같은 트랜잭션 도메인 메서드 |
| 주문 체결(조회+snapshot+재고차감) | `OrderPlacementService`(domain svc) | Product/Inventory 포트·도메인 메서드 조합 |
| 도메인 간 참조 | 전부 | DB FK 없음 — soft reference(id) |

---

## 5. 참조

- 구현 규칙 스킬: `.claude/skills/ddd-layered-tdd/SKILL.md` (+ `kent-beck-tdd`)
- 요구사항 SSOT: `docs/week2/01-requirements.md`
- 개념/아키텍처: `docs/week3/02-ddd-concepts.html`, `docs/week3/03-architecture.html`
- 어휘·약어·호칭: `docs/ubiquitous-language.md`
- 토대: `BaseEntity`(persistence-core), `JpaConfig`(modules:jpa), `CoreException`(supports:error)
