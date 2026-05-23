# Week 2 요구사항 명세

> 4개 도메인(Brand · Product · Likes · Orders)의 유저 시나리오 기반 기능 정의 및 요구사항 명세.
>
> **SSOT(Single Source of Truth) = `docs/week2/{도메인}/*-final.html`.** 본 문서는 그 4개 HTML을 한 곳에서 조망하기 위한 종합 본이며, 시나리오/모델/응답 정의가 충돌하면 항상 `*-final.html`이 정답입니다.
>
> 본 문서는 코드 구현 가이드가 아니라 **요구사항/계약** 문서입니다. JPA 매핑·인덱스·동시성 메커니즘 등 구현 디테일은 본 문서 범위가 아니며, 각 도메인 final HTML 또는 추후 구현 노트가 담당합니다.

---

## 1. 범위와 비범위

### 1.1 본 주차 범위

| 도메인 | 범위 |
|---|---|
| Brand | 관리자 CRUD + 사용자 단건 조회 |
| Product | 관리자 CRUD + 사용자 목록/단건 조회 (정렬/필터) |
| Likes | 로그인 사용자 토글(LIKE/UNLIKE) + 본인 목록 조회 |
| Orders | 로그인 사용자 주문 생성 + 본인 주문 조회 / 관리자 주문 조회 |

### 1.2 명시적 비범위 (후속 카드)

- 결제 / 취소 / 환불 / 부분 환불 (Orders O-F1~F2)
- 멱등키(Idempotency-Key) — 결제 도입 시점에 함께 (O-?6)
- 쿠폰 적용 (O-F3)
- Brand staff(Brand = Tenant) Role 분리 (B-F1 · P-F1 · L-F2 · O-F5)
- 행동 데이터 기반 랭킹/추천 (P-F2 · L-F1 · O-F4)
- 변경 이력의 외부 노출 endpoint (P-F4)
- Inventory 도메인 내부 결정 (단일 quantity vs available/reserved, 동시성 메커니즘, 멀티-warehouse) — 본 주차는 **인터페이스만** 합의

---

## 2. 행위자 (Actor) · 인증

`docs/ubiquitous-language.md` §1·§3 준수. 본 문서 본문에서는 아래 어휘만 사용합니다.

| 어휘 | 정의 | 인증 헤더 | Filter |
|---|---|---|---|
| **사용자** | 일반 사용자, 로그인 여부 무관 | — | — |
| **로그인 사용자** | 인증된 사용자 | `X-Loopers-LoginId` · `X-Loopers-LoginPw` | `AccountHeaderAuthenticationFilter` |
| **관리자** | Platform Admin (단일 계정) | `X-Loopers-Ldap=loopers.admin` | `AdminLdapAuthenticationFilter` |

### 통일 문구 (시나리오 본문 한 글자도 다르지 않게)

- 관리자 인증 실패: "관리자 LDAP 헤더(`X-Loopers-Ldap`)가 없거나 값이 `loopers.admin`이 아니면 예외가 발생한다." → **401**
- 로그인 인증 실패: "로그인 인증 헤더(`X-Loopers-LoginId` / `X-Loopers-LoginPw`)가 없거나 잘못된 요청이면 예외가 발생한다." → **401**

---

## 3. 공통 규약 (`docs/conventions.md` 요약)

### 3.1 URL

| 채널 | Prefix | 예시 |
|---|---|---|
| 사용자 API | `/api/v1/` | `/api/v1/products/{productId}` |
| 관리자 API | `/api-admin/v1/` | `/api-admin/v1/products/{productId}` |

### 3.2 FK 제약 미설정 (전체 도메인 공통)

- 모든 ID 컬럼(`brand_id`, `product_id`, `user_id`, ...)은 **soft reference**. DB FK 제약을 두지 않습니다.
- 무결성 · cascade는 **애플리케이션 레이어** 책임 (예: `BrandService.delete` → `ProductService.softDeleteByBrand`).

### 3.3 BaseEntity 표준 컬럼

- 표준 4컬럼: `createdAt`, `updatedAt`, `createdBy`, `updatedBy`.
- 자동 채움. `createdBy`/`updatedBy`에는 어드민 헤더(`X-Loopers-Ldap`) 또는 사용자 헤더(`X-Loopers-LoginId`) 식별자 저장.
- **외부 응답 DTO 비노출** — 내부 메타데이터.
- **예외**: Orders는 부분 채택(§7.4 참조).

### 3.4 삭제 정책

- **기본**: Soft delete + 2-state machine (`status: ACTIVE / DELETED`). `ACTIVE → DELETED` 단방향, 복원 시나리오 없음.
- **예외 (Likes)**: 토글 본질이라 hard delete. 이력은 별도 도메인 `LikeEvent`(append-only)가 담당.

### 3.5 변경 이력 누적

| 도메인 | 이력 | 적재 방식 |
|---|---|---|
| Brand | `brand_history` (after-only snapshot) | CUD 시 비동기 append |
| Product | `product_history` (after-only snapshot) | CUD 시 비동기 append |
| Likes | `LikeEvent` (LIKE / UNLIKE) | 토글 시 비동기 append |
| Orders | 미설계 (후속 — 결제 도메인과 함께) | — |

- 비동기 적재 실패 시 본 트랜잭션은 성공, 실패는 로깅. (인프라 디테일은 본 주차 범위 밖)
- history 외부 노출 endpoint 없음 (현 시점).

### 3.6 응답 래핑

- 컨트롤러는 도메인 응답 DTO 또는 no body를 반환합니다. `ApiResponse` 래핑은 `ResponseBodyAdvice`가 담당.
- 공통 Jackson `NON_NULL` 정책 준수: nullable 응답 필드는 null이면 생략.

### 3.7 예외 처리

- 비즈니스/애플리케이션 실패는 `CoreException` 서브클래스(`BadRequestException` / `UnauthorizedException` / `ForbiddenException` / `NotFoundException` / `ConflictException` / `InternalServerException`)로만 던집니다.
- 도메인 에러 코드는 owning 도메인 모듈의 enum으로 정의하고 `ErrorCode`를 구현 (예: `BrandErrorCode`, `ProductErrorCode`, ...).

### 3.8 식별자 어휘 (외부 ↔ 내부)

| 외부 (URL · JSON · 시나리오 본문) | 내부 (코드 · DB) | 비고 |
|---|---|---|
| `userId` | `accountId` | 응답 JSON은 `userId`, 내부는 `accountId` |
| `loginId` | `loginId` | 동일 |

---

## 4. Brand 도메인

> SSOT: `docs/week2/01-brand/01-brand-final.html`.

### 4.1 유저 시나리오 (14건)

#### CREATE — 관리자 (4건)

| ID | 동작 | 결과 |
|---|---|---|
| S-C1 | 관리자가 브랜드를 생성한다. (변경 이력 비동기 append) | 201 |
| S-C2 | 관리자 LDAP 헤더가 없거나 값이 `loopers.admin`이 아니면 예외 | 401 |
| S-C3 | 이미 존재하는 `brandName`으로 등록 시도 | 409 |
| S-C4 | 필수 필드 누락/형식 오류 | 400 |

#### READ — 사용자 · 관리자 (5건)

| ID | 동작 | 결과 |
|---|---|---|
| S-R1 | 사용자가 단건 브랜드 조회 | 200 |
| S-R2 | 관리자가 브랜드 목록을 페이지네이션 조회 | 200 |
| S-R3 | 관리자가 단건 브랜드 조회 (사용자보다 풍부한 응답) | 200 |
| S-R4 | 존재하지 않는 `brandId` 조회 | 404 |
| S-R5 | `brandId` 형식 오류 | 400 |

#### UPDATE — 관리자 (3건)

| ID | 동작 | 결과 |
|---|---|---|
| S-U1 | 관리자가 브랜드 정보 수정 (`brandId` 불변, 이력 비동기 append) | 200 |
| S-U2 | 존재하지 않는 브랜드 수정 | 404 |
| S-U3 | 중복된 `brandName`으로 변경 시도 | 409 |

#### DELETE (soft) — 관리자 (2건)

| ID | 동작 | 결과 |
|---|---|---|
| S-D1 | 관리자가 브랜드를 삭제 → `status: ACTIVE → DELETED`. 해당 브랜드의 Product도 application cascade로 `DELETED`로 전이. 이력 비동기 append. | 204 |
| S-D2 | 존재하지 않는 브랜드 삭제 | 404 |

### 4.2 도메인 모델

**Brand**: `id` · `name` (UK) · `description?` · `logoUrl?` · `status (ACTIVE/DELETED)` · BaseEntity 4컬럼.

**Invariant**:
- `name` 유일성 (S-C3, S-U3)
- `name` 길이 1~50자 (S-C4)
- `id` 불변 (S-U1)
- `status` 전이는 `ACTIVE → DELETED` 단방향

**BrandHistory** (after-only snapshot · append-only · 비동기):
- `id` · `brandId` (soft ref) · `name` · `description?` · `action (CREATE/UPDATE/DELETE)` · `actor` · `recordedAt`.
- Brand 삭제 후에도 row 유지.

### 4.3 API endpoint

| Method | Path | 행위자 | 시나리오 |
|---|---|---|---|
| POST | `/api-admin/v1/brands` | 관리자 | S-C1~C4 |
| GET | `/api/v1/brands/{brandId}` | 사용자 | S-R1, R4, R5 |
| GET | `/api-admin/v1/brands` | 관리자 | S-R2 |
| GET | `/api-admin/v1/brands/{brandId}` | 관리자 | S-R3, R4, R5 |
| PUT | `/api-admin/v1/brands/{brandId}` | 관리자 | S-U1~U3 |
| DELETE | `/api-admin/v1/brands/{brandId}` | 관리자 | S-D1~D2 |

### 4.4 응답 DTO 분리

- `BrandPublicResponse`: `id` · `name` · `description` · `logoUrl`
- `BrandAdminResponse`: 위 + `contactEmail` · 집계 필드 등 (B-?2)

### 4.5 결정 이력

| ID | 결정 |
|---|---|
| B-?1 | Brand 도메인 필드: `id` + `name` + `description` + `logoUrl` + `contactEmail`(관리자 응답만) + BaseEntity |
| B-?2 | 사용자 vs 관리자 응답 DTO 분리 (Public / Admin) |
| B-?3 | Soft delete + application cascade |
| B-?4 | `brandName` 유일성 강제 → 409 |

---

## 5. Product 도메인

> SSOT: `docs/week2/02-product/02-product-final.html`.

### 5.1 유저 시나리오 (19건)

#### CREATE — 관리자 (4건)

| ID | 동작 | 결과 |
|---|---|---|
| P-C1 | 관리자가 이미 등록된 `brandId` 아래에 상품 생성 (이력 비동기 append) | 201 |
| P-C2 | 관리자 LDAP 헤더 누락/불일치 | 401 |
| P-C3 | 존재하지 않는 `brandId`로 상품 등록 시도 | 404 |
| P-C4 | 필수 필드 누락/형식 오류 | 400 |

#### READ — 사용자 · 관리자 (8건)

| ID | 동작 | 결과 |
|---|---|---|
| P-R1 | 사용자가 단건 상품 조회 | 200 |
| P-R2 | 사용자가 상품 목록 페이지네이션 조회 (기본 `sort=latest`) | 200 |
| P-R3 | 사용자가 `brandId` 쿼리로 특정 브랜드 상품 필터링 | 200 |
| P-R4 | 사용자가 `sort=price_asc` / `likes_desc` 정렬 (선택 구현) | 200 |
| P-R5 | 관리자가 상품 목록 페이지네이션 + `brandId` 필터 조회 | 200 |
| P-R6 | 관리자가 단건 상품 조회 (사용자보다 풍부한 응답) | 200 |
| P-R7 | 존재하지 않는 `productId` 조회 | 404 |
| P-R8 | `productId` / `brandId` / `page` / `size` 형식 오류 | 400 |

#### UPDATE — 관리자 (4건)

| ID | 동작 | 결과 |
|---|---|---|
| P-U1 | 관리자가 상품 수정 (`brandId` 불변, 이력 비동기 append) | 200 |
| P-U2 | 요청에 다른 `brandId`가 포함되어도 변경되지 않음 (무시 또는 거부) | 400/무시 |
| P-U3 | 존재하지 않는 `productId` 수정 | 404 |
| P-U4 | 관리자 LDAP 헤더 누락/불일치 | 401 |

#### DELETE (soft) — 관리자 (3건)

| ID | 동작 | 결과 |
|---|---|---|
| P-D1 | 관리자가 상품 삭제 → `status: ACTIVE → DELETED`. 주문 스냅샷은 보존되므로 과거 주문 무영향. 이력 비동기 append. | 204 |
| P-D2 | 존재하지 않는 `productId` 삭제 | 404 |
| P-D3 | 관리자 LDAP 헤더 누락/불일치 | 401 |

### 5.2 도메인 모델

**Product**: `id` · `brandId` (soft ref) · `name` · `price` · `description?` · `likeCount` (비정규화) · `status` · BaseEntity 4컬럼.

> **재고 수량은 Product 필드가 아닙니다.** 별도 `Inventory` 도메인(`inventory.product_id` UK, 1:1)이 소유. 본 주차는 인터페이스만 합의(`InventoryService.decreaseAll(items)`), 내부 결정(단일 quantity / 동시성 / 이력)은 후속.

**Invariant**:
- `brandId`는 존재하는 brand여야 함 (P-C1, P-C3)
- `brandId` 생성 후 불변 (P-U1, P-U2)
- `price >= 0` (P-C4)
- `name` 길이 1~100자
- `status` 전이 `ACTIVE → DELETED` 단방향

**ProductHistory** (after-only snapshot · append-only · 비동기):
- `id` · `productId` (soft ref) · `brandId` · `name` · `price` · `stock` (Inventory에서 조회한 snapshot) · `action` · `actor` · `recordedAt`.

### 5.3 API endpoint

| Method | Path | 행위자 | 시나리오 |
|---|---|---|---|
| POST | `/api-admin/v1/products` | 관리자 | P-C1~C4 |
| GET | `/api/v1/products` | 사용자 | P-R2~R4, P-R8 |
| GET | `/api/v1/products/{productId}` | 사용자 | P-R1, P-R7, P-R8 |
| GET | `/api-admin/v1/products` | 관리자 | P-R5, P-R8 |
| GET | `/api-admin/v1/products/{productId}` | 관리자 | P-R6, P-R7, P-R8 |
| PUT | `/api-admin/v1/products/{productId}` | 관리자 | P-U1~U4 |
| DELETE | `/api-admin/v1/products/{productId}` | 관리자 | P-D1~D3 |

### 5.4 정렬 · 필터

- `sort` (화이트리스트 enum, 잘못된 값 → 400)
  - `latest` (기본 · `created_at DESC`)
  - `price_asc`
  - `likes_desc` (P-?5 비정규화 채택 — `product.like_count` 컬럼 사용)
- `brandId` 필터: 사용자 · 관리자 양쪽 지원

### 5.5 응답 DTO 분리

- `ProductPublicResponse`: 사용자용. 재고는 derived 표현 (`inStock` / `stockStatus`)
- `ProductAdminResponse`: 관리자용. 위 + 정확 수량(`stockQuantity`) + 감사 필드

### 5.6 결정 이력

| ID | 결정 |
|---|---|
| P-?1 | Product 필드: `brandId` + `name` + `price` + `description` + `imageUrl` + BaseEntity. 다중 이미지/상세 본문은 후속 |
| P-?2 | 사용자 / 관리자 응답 DTO 분리 (Public / Admin). 재고는 사용자에게 derived만 |
| P-?3 | 정렬: `latest`(필수) + `price_asc` / `likes_desc`(선택). 화이트리스트 enum |
| P-?4 | 재고는 별도 `Inventory` 도메인 / `inventory` 테이블 (1:1). `product.stock` 두지 않음 |
| P-?5 | `product.like_count` 비정규화 (POST/DELETE like 시점 증감) |
| P-?6 | Soft delete (status ACTIVE/DELETED) |
| P-?7 | brand 미존재 응답: 본 주차는 404 only (`BRAND_NOT_FOUND`). 비활성=409는 도입 시점에 검토 |

---

## 6. Likes 도메인

> SSOT: `docs/week2/03-likes/03-likes-final.html`.
>
> Likes는 toggle 도메인 — UPDATE 그룹이 존재하지 않습니다. 모든 동작이 `user_required: O`.

### 6.1 유저 시나리오 (14건)

#### CREATE — 좋아요 등록 (5건)

| ID | 동작 | 결과 |
|---|---|---|
| L-C1 | 로그인 사용자가 상품에 좋아요 등록 (LikeEvent 비동기 append) | 200/201 |
| L-C2 | 로그인 인증 헤더 누락/오류 | 401 |
| L-C3 | 존재하지 않는 `productId`로 좋아요 시도 | 404 |
| L-C4 | 이미 좋아요한 상품에 다시 시도 (멱등 200) | — |
| L-C5 | `productId` 형식 오류 | 400 |

#### DELETE — 좋아요 취소 (5건)

| ID | 동작 | 결과 |
|---|---|---|
| L-D1 | 로그인 사용자가 좋아요 취소 (LikeEvent 비동기 append) | 200/204 |
| L-D2 | 로그인 인증 헤더 누락/오류 | 401 |
| L-D3 | 좋아요한 적 없는 상품에 대해 취소 시도 (멱등 204) | — |
| L-D4 | 존재하지 않는 `productId`로 취소 시도 | 404 |
| L-D5 | `productId` 형식 오류 | 400 |

#### READ — 본인 좋아요 목록 (4건)

| ID | 동작 | 결과 |
|---|---|---|
| L-R1 | 로그인 사용자가 본인의 좋아요 상품 목록 조회 | 200 |
| L-R2 | 로그인 인증 헤더 누락/오류 | 401 |
| L-R3 | 로그인 사용자가 본인이 아닌 다른 `userId`로 조회 시도 | 403 (L-?5) |
| L-R4 | 존재하지 않는 `userId` 형식의 path | 400/404 |

### 6.2 도메인 모델

**ProductLike** (현재 상태 · hard delete):
- `id` · `userId` (soft ref) · `productId` (soft ref) · `createdAt` (BaseEntity 부분 채택).
- **UK**: `(user_id, product_id)` — 도메인 유일성.
- **status 컬럼 없음** · state machine 없음. UNLIKE 시 row hard delete, 다시 LIKE 시 새 row INSERT.

**LikeEvent** (append-only · 비동기):
- `id` · `userId` (soft ref) · `productId` (soft ref) · `action (LIKE / UNLIKE)` · `recordedAt`.
- LIKE/UNLIKE 모두 새 row append. ProductLike와 별개로 보존.

**Product.likeCount 비정규화** (L-?3 / P-?5와 통일):
- 토글 시점에 `product.like_count` 증감.
- `sort=likes_desc` 정렬 비용을 매 요청 COUNT(*) 대신 컬럼 조회로 처리.

### 6.3 API endpoint

| Method | Path | 행위자 | 시나리오 |
|---|---|---|---|
| POST | `/api/v1/products/{productId}/likes` | 로그인 사용자 | L-C1~C5 |
| DELETE | `/api/v1/products/{productId}/likes` | 로그인 사용자 | L-D1~D5 |
| GET | `/api/v1/users/{userId}/likes` | 로그인 사용자(본인만) | L-R1~R4 |

### 6.4 결정 이력

| ID | 결정 |
|---|---|
| L-?1 | 중복 좋아요: 멱등 200. UK는 도메인 invariant로 유지 |
| L-?2 | 좋아요 안 한 상품 취소: 멱등 204 |
| L-?3 | `product.like_count` 비정규화 + 토글 시 증감 (P-?5 통일) |
| L-?5 | 타 사용자 `userId` 접근: 403 FORBIDDEN (학습 컨텍스트 — 의미 정합성 우선. 운영 보안 단계에서 404로 전환 가능) |
| L-?6 | 좋아요 등록 응답: 200 OK + Body (좋아요 상태 / likeCount) |
| L-?7 | LikeEvent 적재: 비동기 append, 실패 시 로깅 |

---

## 7. Orders 도메인

> SSOT: `docs/week2/04-orders/04-orders-final.html`.
>
> **본 주차 범위: 주문 생성 + 조회.** UPDATE/DELETE(결제/취소/환불)는 후속(§1.2).

### 7.1 유저 시나리오 (17건)

#### CREATE — 사용자 주문 요청 (7건)

| ID | 동작 | 결과 |
|---|---|---|
| O-C1 | 로그인 사용자가 여러 상품을 한 번에 주문 → 주문 시점 상품 정보가 스냅샷으로 보존, 재고 차감 | 201 |
| O-C2 | 로그인 인증 헤더 누락/오류 | 401 |
| O-C3 | `items` 중 하나라도 존재하지 않는 `productId` → 주문 전체 거부 | 404 |
| O-C4 | 재고 부족 상품 하나라도 있으면 주문 전체 거부 | 409 |
| O-C5 | `quantity < 1` (0 또는 음수) | 400 |
| O-C6 | `items` 배열 비어있거나 누락 | 400 |
| O-C7 | 동시 주문 몰려도 재고 차감 누락 없이 보장 (본 주차는 인터페이스만 명시) | 201 |

#### READ — 사용자 본인 주문 조회 (6건)

| ID | 동작 | 결과 |
|---|---|---|
| O-R1 | 로그인 사용자가 본인 주문 목록을 `startAt`/`endAt` 날짜 범위 조회 | 200 |
| O-R2 | 로그인 사용자가 본인 주문 단건을 `orderId`로 조회 | 200 |
| O-R3 | 날짜 범위 파라미터 누락/형식 오류 | 400 |
| O-R4 | 타 사용자의 `orderId`로 조회 시도 | 404 (O-?7) |
| O-R5 | 존재하지 않는 `orderId` 조회 | 404 |
| O-R6 | 로그인 인증 헤더 누락/오류 | 401 |

#### READ — 관리자 주문 조회 (4건)

| ID | 동작 | 결과 |
|---|---|---|
| O-R7 | 관리자가 전체 주문 목록 페이지네이션 조회 | 200 |
| O-R8 | 관리자가 단건 주문 상세 조회 (사용자보다 풍부한 응답) | 200 |
| O-R9 | 관리자 LDAP 헤더 누락/불일치 | 401 |
| O-R10 | 관리자가 존재하지 않는 `orderId` 조회 | 404 |

### 7.2 도메인 모델

**Order**: `id` · `userId` (soft ref) · `orderedAt` · `totalAmount` · `status` (본 주차 `CREATED` 단일) · `createdAt` · `updatedAt`.

**OrderItem** (주문 시점 스냅샷):
- `id` · `orderId` (FK · ON DELETE CASCADE 의미적으로; 실제 DB FK는 §3.2에 따라 없음) · `productId` (soft ref) · `brandId` (soft ref · snapshot) · `productName` (snapshot) · `brandName` (snapshot) · `unitPrice` (snapshot) · `quantity` (≥ 1) · `createdAt`.

**Invariant**:
- Order.items 최소 1개 (O-C6)
- OrderItem.quantity ≥ 1 (O-C5)
- 주문 시점 스냅샷(productName/unitPrice/brandName)은 생성 이후 불변 (O-C1)
- `Order.totalAmount = Σ(item.unitPrice × item.quantity)` (O-C1)
- 주문 생성 트랜잭션 내에서 **Inventory 도메인의 재고 확인 + 차감**이 모두 성공해야 함 (O-C1, O-C4, O-C7)
- 주문 단건 조회는 `Order.userId === 요청자 userId`여야 함 (O-R4)

**Product → OrderItem 관계** (soft reference + snapshot):
- Product가 brand cascade로 soft delete(`status=DELETED`)되어도 OrderItem snapshot은 그대로 유지 → 과거 주문 기록 무영향.

### 7.3 API endpoint

| Method | Path | 행위자 | 시나리오 |
|---|---|---|---|
| POST | `/api/v1/orders` | 로그인 사용자 | O-C1~C7 |
| GET | `/api/v1/orders?startAt=&endAt=` | 로그인 사용자 | O-R1, O-R3, O-R6 |
| GET | `/api/v1/orders/{orderId}` | 로그인 사용자 (본인만) | O-R2, O-R4, O-R5, O-R6 |
| GET | `/api-admin/v1/orders` | 관리자 | O-R7, O-R9 |
| GET | `/api-admin/v1/orders/{orderId}` | 관리자 | O-R8, O-R9, O-R10 |

### 7.4 BaseEntity 부분 채택

- **Order**: `createdAt` + `updatedAt`만 사용. `updatedAt`은 미래 status 변경 추적용(O-?1).
- **OrderItem**: 스냅샷 immutable이라 `createdAt`만.
- 양쪽 모두 `createdBy`/`updatedBy` 미사용 — 행위자는 `userId`가 보유, 감사는 미래 OrderEvent(append-only) 책임.

### 7.5 응답 DTO 분리

- `OrderResponse`: 사용자용. `orderId` · `orderedAt` · `totalAmount` · `items[]`
- `OrderAdminResponse`: 관리자용. 위 + `userId` · `status` · `createdAt`/`updatedAt`

### 7.6 결정 이력

| ID | 결정 |
|---|---|
| O-?1 | 주문 상태 enum 자리는 잡되 본 주차는 `CREATED` 단일. 결제 도입 시 enum 값 추가 |
| O-?2 | 재고 차감 시점: 주문 생성 시 즉시 차감 (`InventoryService.decreaseAll(items)`을 주문 트랜잭션 내 호출) |
| O-?3 | 동시 주문 경합: 본 주차는 인터페이스/시그니처만 보장. 동시성 구현은 Inventory 도메인 내부 후속 결정 |
| O-?4 | 스냅샷 범위: 필수 `productId` · `productName` · `unitPrice` · `quantity` / 강추 `brandId` · `brandName` |
| O-?5 | 사용자 / 관리자 응답 DTO 분리 |
| O-?6 | 멱등키(Idempotency-Key): 본 주차 미도입. 결제 도입 시 함께 |
| O-?7 | 타 사용자 주문 접근: **404** (주문은 PII 성격 강함. ID enumeration 차단) |
| O-?8 | 본인 주문 목록 날짜 범위: `startAt`/`endAt` 둘 다 필수. max range 제약은 본 주차 미도입 |
| O-?9 | OrderItem 모델링: `@Entity` (Order `@OneToMany` OrderItem) |

---

## 8. 도메인 간 관계

### 8.1 의존 그래프 (논리)

```
Brand ──(1:N soft ref)──> Product ──(1:1 soft ref)──> Inventory
                              │
                              ├──(N:1 snapshot)──< OrderItem ──(N:1)──> Order ──(N:1 soft ref)──> User(Account)
                              │
                              └──(1:N soft ref)──< ProductLike ──(N:1 soft ref)──> User(Account)
                                                                   │
                                                                   └─── (비정규화) product.like_count
```

### 8.2 Cascade 매트릭스 (application 레벨)

| 트리거 | 영향받는 도메인 | 동작 |
|---|---|---|
| Brand soft delete (S-D1) | Product | 해당 brand의 모든 product도 `status=DELETED`로 함께 전이 |
| Product soft delete (P-D1) | Inventory | Inventory 행도 application cascade로 archive (구체 동작은 Inventory 도메인 결정) |
| Product soft delete (P-D1) | OrderItem | 영향 없음 — snapshot이 이미 보존됨 |
| Product soft delete (P-D1) | ProductLike / LikeEvent | 본 주차 결정 보류 (cascade 정책 미정) |

### 8.3 Order 생성 트랜잭션 내 협업

`POST /api/v1/orders` 한 트랜잭션 안에서:

1. Product 조회 → 스냅샷 생성 (`productName`, `unitPrice`, `brandId`, `brandName`)
2. `InventoryService.decreaseAll(items)` 호출 → 재고 부족 시 `ConflictException(STOCK_INSUFFICIENT)` 던지고 전체 롤백
3. Order + OrderItem persist
4. (비동기 후속) LikeEvent / BrandHistory / ProductHistory 등 본 주차 범위에 포함 안 됨

---

## 9. 미해결 / 후속 결정 카드 요약

| ID | 항목 | 본 주차 결정 | 후속 트리거 |
|---|---|---|---|
| O-?1 | Order status enum 확장 | `CREATED` 단일 | 결제 도입 |
| O-?3 | 동시성 구현 (락 / 원자 UPDATE / MQ) | 인터페이스만 | Inventory 도메인 내부 후속 |
| O-?6 | 멱등키 | 미도입 | 결제 PG 연동 |
| O-?8 | 주문 날짜 범위 max | 제약 없음 | 성능 이슈 관측 시 |
| P-F1 / B-F1 / L-F2 / O-F5 | Brand staff (Brand = Tenant) Role 분리 | Role enum 미도입 | Brand staff 도입 |
| P-F2 / L-F1 / O-F4 | 행동 데이터 기반 랭킹/추천 비동기 집계 | 미설계 | 비동기 집계 도메인 도입 |
| P-F4 | Brand/Product history 외부 노출 endpoint | 미노출 | seller 도입 |
| O-F3 | 쿠폰 적용 | 미도입 | 쿠폰 도메인 도입 |

---

## 10. 참조

- 4개 도메인 final HTML (SSOT)
  - `docs/week2/01-brand/01-brand-final.html`
  - `docs/week2/02-product/02-product-final.html`
  - `docs/week2/03-likes/03-likes-final.html`
  - `docs/week2/04-orders/04-orders-final.html`
- `docs/big-picture.md` — 큰 그림 / 추가 비전
- `docs/ubiquitous-language.md` — 어휘 사전 (행위자 · 인증 · 식별자 · URL · 표기)
- `docs/conventions.md` — 영속성 / 모델링 공통 규칙
- `CLAUDE.md` — 작업 가이드 (에러 처리 · 디자인 원칙 · 테스트 전략)
