# Loopers Ubiquitous Language Dictionary

> 본 프로젝트의 도메인/설계/API 산출물에서 **반드시 동일하게 써야 하는 어휘**.
> 시나리오 HTML, 도메인 모델, DB 스키마, 코드, PR 본문, 회의 발언 모두 동일 어휘로 정렬한다.
>
> **충돌 시 우선순위**: 본 문서 > 개별 산출물 > 과거 작성물.
> 본 문서를 수정한 후에는 영향받는 산출물도 같은 PR에서 함께 수정한다.

## 0. 사용 규칙

- **새 산출물 작성 전 본 문서를 먼저 참조한다** (행위자/인증/식별자/필드 표기/DTO 명명 확정).
- 어휘 추가/수정이 필요하면 본 문서를 먼저 갱신하고, 그 다음 산출물에 반영한다.
- 본 문서에 정의되지 않은 어휘는 "결정 카드"(시나리오의 `B-?N` 패턴 또는 본 문서의 `D-?N` 패턴)로 표시하고, 결정되는 시점에 본 문서에 합류시킨다.
- 본 문서가 정한 단어는 **번역하지 않고 그대로 쓴다** (한글/영문 표기 모두 사전 그대로).

---

## 1. 행위자 (Actor)

| 어휘 | 정의 | 인증 | 사용 위치 |
|------|------|------|-----------|
| **사용자** | 우리 서비스를 사용하는 일반 사용자. **로그인 여부와 무관**. | 시나리오/상황에 따라 다름 | 시나리오 본문, 시퀀스 다이어그램 |
| **로그인 사용자** | 인증된 사용자. `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 식별. | Login | 인증 필요한 동작의 시나리오 본문 |
| **관리자** | 운영자 계정. Platform Admin. `X-Loopers-Ldap=loopers.admin` 헤더로 식별. | LDAP | 관리자 API 시나리오 본문 |
| **Brand staff** (미래) | 특정 brand 운영자. Tenant 비전. **본 주차 범위 외**. | (미정) | 미래 카드(`X-F1`)에서만 |

### 금지 어휘 → 통일 어휘

| 금지 | 통일 | 이유 |
|------|------|------|
| **대고객** / 대고객 | **사용자** | 한국어 자연 표현이 아님. 일상 어휘로 통일 |
| **로그인한 대고객** | **로그인 사용자** | 위와 동일 |
| 비로그인 사용자 / 비로그인 유저 / 로그인 안 한 사용자 | **사용자** | 로그인 안 한 상태도 사용자다 |
| 로그인 유저 / 로그인한 사용자 (불완전 표현) / 로그인한 유저 | **로그인 사용자** | 행위자 통일 |
| **어드민** / 운영자 / 어드민 계정 / 어드민이 아닌 계정 | **관리자** | 한국어 자연 표현으로 통일 |
| 사용자 (모호) — 로그인이 필요한 동작에서 | **로그인 사용자** | 인증 컨텍스트에서는 항상 "로그인 사용자" |
| 유저 / 고객 / 회원 (한국어) | **사용자** | "회원가입" 같은 굳은 표현은 예외적으로 허용 |

> 부정 어휘 (`X가 아닌 계정`)는 **인증 헤더 기준의 긍정 어휘**로 재작성한다.
> 예: "관리자가 아닌 계정이 등록을 시도하면" → "LDAP 헤더가 없거나 값이 `loopers.admin`이 아니면"

### 외부 어휘는 유지 — 정정 대상 아님

- API path `/api/v1/users/{userId}` 의 `users`, `userId` — **외부 평가 스펙**이므로 유지 (§4 매핑 참조)
- `user_required: O/X` — 평가 스펙 표 헤더이므로 유지
- 관리자 API prefix `/api-admin/v1/` — 코드 식별자라 유지 (한국어 본문에서 "관리자 API" 로 부른다)
- 헤더 값 `loopers.admin` — 코드 식별자이므로 유지

---

## 2. Role 어휘 (Permission Axis)

big-picture.md의 "추가 비전" Role 분리. 권한 축 명사는 **영문 그대로** 쓴다 (한글 의역 금지).

| Role | 정의 | 한국어 행위자 (§1) | 도입 시점 |
|------|------|-------------------|----------|
| **Customer** | 본인 데이터(주문/좋아요)만 접근하는 사용자 | 사용자 / 로그인 사용자 | Week 1 완료 |
| **Platform Admin** | 모든 Brand/Product/Order CRUD 권한 (단일 계정 `loopers.admin`) | 관리자 | Week 2 |
| **Brand Staff** | 자기 Brand 내 데이터만 접근 (= Brand=Tenant 미래 가정) | Brand staff (미래) | 미래 |

- **Tenant** = Brand. "입주 단위", "Brand staff" 표현은 모두 이 축에 종속.
- "권한 축", "권한 격리의 축"이라는 표현으로 `brand_id`의 위상을 가리킨다.
- 본 주차는 **Customer + Platform Admin** 두 단계만 활성. Brand Staff는 명시적 미래 항목.

---

## 3. 인증 (Authentication)

### 두 가지 인증 축

| 축 | 헤더 | 필터 (정식 클래스명) | 적용 API |
|----|------|----------------------|----------|
| **LDAP 인증** | `X-Loopers-Ldap` (값: `loopers.admin`) | `AdminLdapAuthenticationFilter` ※ Week 2 작업 예정 | `/api-admin/v1/**` |
| **로그인 인증** | `X-Loopers-LoginId` + `X-Loopers-LoginPw` | `AccountHeaderAuthenticationFilter` ※ 구현됨 | 로그인 필요 `/api/v1/**` |

> 본문/주석/PR 설명/시퀀스 다이어그램 모두 **정식 클래스명만 사용**한다. 약어/별칭 금지 (§9 참조). `LoginAuthFilter`, `LdapAuthFilter` 같은 축약형은 사용하지 않는다.

### 시나리오 본문 통일 문구 (한 글자도 다르지 않게 사용)

**LDAP 인증 실패 시나리오:**
> "관리자 LDAP 헤더(`X-Loopers-Ldap`)가 없거나 값이 `loopers.admin`이 아니면 예외가 발생한다."

- 응답 코드: **`401`** (인가 실패가 아닌 인증 실패. 403 사용 금지)
- 작성 위치: **CRUD 그룹의 CREATE 시나리오에 한 번만** 기재. UPDATE/DELETE/READ는 동일 조건 적용으로 간주하고 별도 시나리오로 분리하지 않는다.

**로그인 인증 실패 시나리오:**
> "로그인 인증 헤더(`X-Loopers-LoginId` / `X-Loopers-LoginPw`)가 없거나 잘못된 요청이면 예외가 발생한다."

- 응답 코드: **`401`**
- 작성 위치: LDAP과 동일하게 CREATE에 한 번만 (단, Likes처럼 모든 동작이 로그인 필요인 경우 도메인 lead 문장에 "모든 동작이 `user_required: O`"로 명시 가능).

### 시퀀스 다이어그램 메시지 통일

| 상황 | 메시지 |
|------|--------|
| LDAP 필터 통과 | `LDAP 헤더 검증 통과` |
| LDAP 필터 차단 | `401 UNAUTHORIZED` |
| Login 필터 통과 | `Login 헤더 검증 통과 · accountId 주입` |
| Login 필터 차단 | `401 UNAUTHORIZED` |

> 시퀀스에서는 외부 path 변수명을 따르되, 필터 통과 후 컨트롤러에 주입되는 식별자는 **`accountId`** 로 명시한다. (§4 참조)

### 인증 보조 컴포넌트

| 어휘 | 정의 |
|------|------|
| `AccountAuthenticationEntryPoint` | 인증 실패를 `ApiResponse.fail` (`CommonErrorCode.UNAUTHORIZED`) 로 변환하는 entry point |
| `AccountPrincipal` | 인증 성공 후 SecurityContext에 저장되는 principal 객체 (accountId, loginId 보유) |
| `@RequestAttribute("accountId")` | controller에서 인증된 식별자를 수신하는 표준 방식 |

### 금지 어휘
- "헤더 통과" / "인증 통과" 단독 → 위 통일 문구 사용 (필터 명칭 명시)
- "관리자가 아닌 계정" / "LDAP 헤더가 없거나 잘못된 요청" 변종 → 위 통일 문구로
- `LoginAuthFilter` / `LdapAuthFilter` — 다이어그램/본문/코드 모두 금지 (§9). 정식 클래스명 사용

---

## 4. 식별자 어휘 (Identifier Mapping)

### 핵심 매핑: 외부 ↔ 내부

| 외부 (API path / 응답 / 시나리오 본문) | 내부 (도메인/코드/DB) | 비고 |
|---------------------------------------|----------------------|------|
| **`userId`** | **`accountId`** | 외부 spec은 `userId`, 내부 도메인은 `account`. 컨트롤러에서 매핑 |
| `loginId` | `loginId` | 동일 (사용자가 입력하는 로그인 ID. `account_credential.identifier`에 저장) |
| `brandId` | `brandId` | 동일 |
| `productId` | `productId` | 동일 |
| `orderId` | `orderId` | 동일 |

- API path `/api/v1/users/{userId}/likes` 의 `{userId}` 는 **내부적으로 `accountId`** 로 받는다.
- 컨트롤러 시그니처에서는 `@PathVariable("userId") accountId: Long` 형식으로 매핑한다 (path 변수명은 외부 spec, 변수명은 내부 도메인).
- 응답 JSON 필드는 **외부 어휘** (`userId`, `loginId`)를 노출한다. 내부 `accountId`는 외부에 노출되지 않는다.

### 본인 검증 어휘

| 어휘 | 정의 |
|------|------|
| **본인** | 인증된 사용자의 accountId가 대상 리소스의 owner accountId와 일치하는 상태 |
| **타 사용자** | 인증된 사용자의 accountId가 대상 리소스의 owner accountId와 다른 상태 |
| **본인 검증** | `authenticatedAccountId === resource.ownerAccountId` 비교 |
| `pathAccountId` | path에서 받은 `{userId}` 값을 내부 accountId로 받은 변수명 |
| `authenticatedAccountId` | 인증 필터를 통과한 현재 요청자의 accountId |

> 시나리오 본문에서는 "본인이 아닌 다른 userId로 ..." 식으로 외부 어휘를 쓰고, 시퀀스 다이어그램 주석에서는 내부 어휘(`accountId`)로 명시한다.

### 금지 어휘
- "유저 ID", "사용자 ID" (모호) → `userId` (외부) 또는 `accountId` (내부) 중 하나로
- "loginUserId" → `authenticatedAccountId`
- "타인의 ID" → "타 사용자의 userId"
- "타 유저" → "타 사용자" (§1 행위자 어휘와 정합)

---

## 5. URL 규약

### Prefix

| 대상 | Prefix | 예시 |
|------|--------|------|
| 사용자 API | `/api/v1/` | `/api/v1/products/{productId}` |
| 관리자 API | `/api-admin/v1/` | `/api-admin/v1/products/{productId}` |

### 표기 규칙

- **모든 위치(라이프사이클 박스, 시퀀스 다이어그램, 결정 카드, 본문 모두)에서 full path로 표기**.
- `GET /products` 같은 prefix 생략 표기는 **금지**.
- 시나리오 본문에서 URL을 언급할 때도 full path. 단, 같은 단락에서 반복 언급 시 두 번째부터는 메서드+패스 핵심만 (예: `GET /api/v1/products/{productId}` 호출 후 ... `해당 GET` ...).
- path 변수명은 **외부 spec**을 따른다: `{userId}`, `{brandId}`, `{productId}`, `{orderId}`.

---

## 6. 필드/컬럼 표기 규약

### 레이어별 표기

| 레이어 | 표기 | 예시 |
|--------|------|------|
| **시나리오 본문** (Section 1) | **camelCase** | `brandId`, `productId`, `orderedAt` |
| **도메인 모델** (Section 3) | **camelCase** | `brandId`, `likeCount`, `unitPrice` |
| **DB 컬럼 명세** (Section 4) | **snake_case** | `brand_id`, `like_count`, `unit_price` |
| **시퀀스 SQL 메시지** | **snake_case** | `SELECT ... WHERE brand_id = ?` |
| **응답 JSON 필드** | **camelCase** (외부 어휘) | `userId`, `loginId`, `brandId` |

### 한 문장 내 혼용 금지
- ❌ "다른 brandId가 포함되어도 brand_id는 변경되지 않는다"
- ✅ "다른 brandId가 포함되어도 brandId는 변경되지 않는다" (시나리오 본문)
- ✅ "다른 brand_id 값이 들어와도 갱신하지 않는다" (DB 관점 노트)

---

## 7. 도메인 / 엔티티 어휘

### 엔티티 명사 (영문 그대로, 한글 의역 금지)

| 도메인 | 엔티티 | DB 테이블 | API 표현 |
|--------|--------|-----------|----------|
| Brand (브랜드) | `Brand` | `brand` | `brand`, `brandId`, `brandName` |
| Product (상품) | `Product` | `product` | `product`, `productId`, `productName` |
| Inventory (재고) | `Inventory` | `inventory` | 외부 노출 X — Product 응답 내 derived (`inStock: boolean` 또는 `stockStatus: enum`). 수량 그대로 노출하지 않는다. |
| Like (좋아요) | `ProductLike` | `product_like` | `like` (동사) / `likes` (목록) — path는 `/likes` |
| Order (주문) | `Order` | `orders` (MySQL 예약어 회피) | `order`, `orderId`, `orderedAt` |
| OrderItem (주문 항목) | `OrderItem` | `order_item` | `items` (배열) |
| Account (계정) | `Account` | `account` | 외부 노출 X (대신 `userId`, `loginId`) |
| AccountCredential (인증 수단) | `AccountCredential` | `account_credential` | 외부 노출 X |

### 도메인 명사 vs 한국어 표현

| 컨텍스트 | 영문 (도메인) | 한국어 (시나리오 본문) |
|---------|---------------|---------------------|
| 코드/엔티티/DB | `Brand`, `Product`, `Inventory`, `Order`, `ProductLike`, `Account` | — |
| 시나리오 본문 | — | 브랜드, 상품, 재고, 주문, 좋아요, (account는 외부 노출 X) |
| API path | brands, products, orders, likes, users | — |

### 도메인 책임 분리 (Account vs User)

- **외부 어휘**: `user`, `userId` — API 평가 스펙(big-picture.md)이 정한 외부 표현
- **내부 어휘**: `Account` / `AccountCredential` — MySQL `mysql.user` 시스템 테이블 + Spring Security `User` 클래스 충돌 회피
- `Account` = 사용자 프로필 (name, birthDate, email)
- `AccountCredential` = 인증 수단 (method=PASSWORD, identifier=loginId, secret=암호화된 비밀번호)
- 회원가입 = `Account` + `AccountCredential` 동시 생성
- 비밀번호 변경 = `AccountCredential` 변경 (Account 프로필 변경 아님)

### 도메인 책임 분리 (Product vs Inventory)

`Product`와 `Inventory`는 하나의 row에 합치지 않고 **별도 도메인 / 별도 테이블**로 둔다. 1:1 관계로 `inventory.product_id`로 연결.

- **`Product`** = 상품 카탈로그 정보 (name, price, description, imageUrl, brandId, likeCount). 어드민 editorial 수정이 주 변경 트리거. read-heavy, 적극 캐싱 가능.
- **`Inventory`** = 재고 수량과 그 변동 (productId, quantity). 주문 차감 / 어드민 입고 / 취소 복원이 주 변경 트리거. write-heavy(주문 시), 캐싱 위험(오버셀링).

**분리 근거 (라이프사이클 / 정합성 / 캐시 전략의 근본 차이):**

| 축 | Product | Inventory |
|---|---|---|
| 변경 트리거 | 사람의 명시적 결정 (editorial) | 시스템 이벤트 (주문/취소) + 어드민 입고 |
| 변경 주체 | 1명 (담당 어드민) | N개 동시 트랜잭션 |
| 변경 단위 | row 전체 교체 (PUT) | 정수 delta (`quantity -= 1`) |
| 정합성 모델 | last-write-wins 허용 | **lost update 절대 금지** (오버셀링) |
| 캐시 전략 | CDN/Redis 적극 캐싱 | 캐싱 위험 — 실시간성 critical |
| 확장 축 | attribute 폭발 (variant/image/SEO) | 상태 분화 (available/reserved/on_hand) + location |

> 분리 근거는 단순 락 경합 회피가 아니다 (InnoDB MVCC에서 일반 SELECT는 X-lock에 막히지 않는다). 본질은 정합성 모델/캐시 전략/확장 축의 차이.

**외부 노출 정책:**

- 재고 수량(`quantity`)은 외부 응답에 그대로 노출하지 않는다.
- Product 응답 DTO에 derived 필드로 표현: `inStock: boolean` 또는 `stockStatus: enum` (`IN_STOCK` / `LOW_STOCK` / `OUT_OF_STOCK`).
- 어드민 응답은 정확 수량 노출 허용 (`stockQuantity: Int`).

**구체 구현 결정 카드 (본 사전 외 영역):**

- 단일 quantity 컬럼 vs `available` + `reserved` 분리 — `P-?4` 또는 신설 `I-?N`
- 차감 시점 (주문 즉시 vs 결제 시 reserve→commit) — `O-?2`
- 동시성 제어 (atomic UPDATE / 비관락 / 낙관락 / 분산락) — `O-?3`
- 이력 테이블(`stock_movement`) 도입 시점 — 미래 카드

### 금지 어휘
- 코드/도메인에 `User` 이름 도입 금지 (충돌 회피). `Account`만 사용.
- `Member` (한국어 "회원" 의역) 금지. 외부 `user` / 내부 `account` 외 명칭 도입 금지.
- 시나리오 본문에서 영문 엔티티명(`Brand`, `Product`, `Inventory`)을 직접 쓰지 않는다 — 한국어 "브랜드", "상품", "재고" 사용.
- `product.stock` 컬럼 / `Product.stock` 필드 금지 — `Inventory`로 분리됨. 시나리오 / 도메인 / DB / 시퀀스 / 응답 DTO 모두 동일.
- `stock` 단독 사용을 시나리오 본문에서 피한다 — "재고" (한국어) 또는 `quantity` (필드명) 사용. `stock` 영문 단어는 외부 derived 표현(`inStock`, `stockStatus`)에서만 허용.

---

## 8. 응답 DTO 명명

### 패턴

| 위치 | 명명 |
|------|------|
| 사용자 응답 | `{Domain}PublicResponse` |
| 관리자 응답 | `{Domain}AdminResponse` |
| 본인 전용 응답 (Likes 등) | `My{Domain}Response` (예: `MyLikesResponse`) |

### 적용 예

| 도메인 | 사용자 | 관리자 |
|--------|--------|--------|
| Brand | `BrandPublicResponse` | `BrandAdminResponse` |
| Product | `ProductPublicResponse` | `ProductAdminResponse` |
| Order | `OrderPublicResponse` | `OrderAdminResponse` |
| Like | `MyLikesResponse` | (해당 없음) |
| Account | `AccountPublicResponse` (필요 시) | (현재 관리자용 없음) |

### 금지
- 접미사 없는 `OrderResponse` / `BrandResponse` → 위 패턴으로
- `OrderDto`, `BrandView`, `OrderSummary` 등 다른 접미사 → 위 패턴으로
- DTO 명에 약어 사용 금지 (§9). 예: `OrderResp`, `BrandPubResp` 등 금지

---

## 9. 약어 금지 (Naming — No Abbreviations)

### 절대 규칙

**클래스/컴포넌트/필터/서비스/리포지토리/컨트롤러/엔티티/DTO 이름은 풀네임(PascalCase) 으로만 표기한다. 약어/축약형/단일 문자 별칭은 모든 산출물(코드/시나리오/시퀀스 다이어그램/PR 본문/회의 발언)에서 금지한다.**

### 금지 약어 → 풀네임

| 금지 | 통일 |
|------|------|
| `Ctrl` / `Ctl` | `Controller` |
| `Svc` | `Service` |
| `Repo` | `Repository` |
| `Mgr` | `Manager` |
| `Cfg` | `Config` |
| `Auth` (단독, 클래스명 일부일 때) | `Authentication` (예: `LoginAuthFilter` → `AccountHeaderAuthenticationFilter`) |
| `Pwd` / `Pw` (클래스명 일부) | `Password` (단, 헤더 키 `X-Loopers-LoginPw` 는 외부 스펙이므로 유지) |
| `Resp` | `Response` |
| `Req` | `Request` |
| `Adv` | `Advice` |
| `Ex` (단독, 예외 클래스명) | `Exception` |
| `Cmd` | `Command` |
| `DB` (시퀀스 participant) | `Database` |

### 금지 사례 → 정정

| 금지 | 정정 |
|------|------|
| `AdminBrandCtrl` | `AdminBrandController` |
| `AdminProductCtrl` | `AdminProductController` |
| `AdminOrderCtrl` | `AdminOrderController` |
| `ApiCtrlAdvice` | `ApiControllerAdvice` |
| `LoginAuthFilter` | `AccountHeaderAuthenticationFilter` |
| `LdapAuthFilter` | `AdminLdapAuthenticationFilter` |
| `BrandSvc` | `BrandService` |
| `BrandRepo` | `BrandRepository` |

### Mermaid 시퀀스 다이어그램 — 단일 문자 alias 금지

❌ **금지** — `participant F as LoginAuthFilter` / `participant Ctl as LikeController` / `participant S as BrandService`
✅ **통일** — `participant AccountHeaderAuthenticationFilter` / `participant LikeController` / `participant BrandService`

다이어그램 화살표도 풀네임으로 표기:
```
❌ F->>A: 헤더 검증 통과
✅ AccountHeaderAuthenticationFilter->>LikeController: 헤더 검증 통과
```

가독성을 이유로 단일 문자/축약형 별칭을 두지 않는다 — 풀네임이 길어도 의미 정합성이 우선이다.

### 허용되는 약어 (예외)

- **HTTP 메서드**: `GET` / `POST` / `PUT` / `PATCH` / `DELETE` (표준)
- **HTTP 상태 코드**: `200` / `201` / `204` / `400` / `401` / `403` / `404` / `409` 등 (표준)
- **DB 약어**: `PK`, `FK`, `UK`, `INDEX`, `CHECK`, `NULL`, `NOT NULL` (ANSI SQL 표준)
- **외부 spec**: `X-Loopers-Ldap`, `X-Loopers-LoginId`, `X-Loopers-LoginPw` (헤더 키), `loopers.admin` (헤더 값), `LDAP` (프로토콜 약어)
- **언어 식별자**: `id`, `url` (소문자 필드명에서는 관용)
- **JPA 관용**: `@OneToMany`, `@ManyToOne`, `@Embeddable`, `@Transactional` 등 (Spring/JPA 어노테이션은 그대로)

> 새 약어 도입은 본 사전의 변경 이력에 반영된 PR을 통해서만.

---

## 10. 시나리오 어휘

### 예외 표현 (시나리오 본문)

| 상황 | 통일 표현 |
|------|----------|
| 모든 예외 | "**~면 예외가 발생한다.**" |

### 금지 어휘 → 통일 어휘

| 금지 | 통일 |
|------|------|
| 거부한다 / 막는다 / 차단한다 / 404를 반환한다 / 401로 응답한다 | **예외가 발생한다** |
| (예외: 멱등 처리 같이 의도적으로 예외가 아닌 경우는 "조용히 무시" 같은 표현 허용) | — |

### 검증 실패 통일 문구

| 상황 | 통일 표현 |
|------|----------|
| 필수 필드 누락 | "필수 필드가 누락되거나 형식이 잘못되면 예외가 발생한다." |
| 형식 오류 (id/path/쿼리) | "{필드명} 형식이 잘못된 요청이면 예외가 발생한다." |
| 미존재 리소스 | "존재하지 않는 {필드명}로 ... 시 예외가 발생한다." |
| 중복 | "이미 존재하는 {필드명}으로 ... 시 예외가 발생한다." |

### CRUD 그룹 헤더 라벨

`"{행위자} {행위}"` 패턴.

| 도메인 | CREATE | READ | UPDATE | DELETE |
|--------|--------|------|--------|--------|
| Brand | 관리자 브랜드 등록 | 조회 | 관리자 브랜드 수정 | 관리자 브랜드 삭제 |
| Product | 관리자 상품 등록 | 조회 | 관리자 상품 수정 | 관리자 상품 삭제 |
| Like | 사용자 좋아요 등록 | 사용자 내 좋아요 조회 | (없음) | 사용자 좋아요 취소 |
| Order | 사용자 주문 요청 | 사용자 본인 주문 조회 / 관리자 주문 조회 | (본 주차 없음) | (본 주차 없음) |
| Account | 사용자 회원가입 | 사용자 내 정보 조회 | 사용자 비밀번호 변경 | (없음) |

### 시나리오 ID 패턴
- 도메인 첫 글자 + CRUD 첫 글자 + 일련번호: `S-C1`, `P-R3`, `L-D2`, `O-?7`
- 결정 필요 항목: `{X}-?N`
- 미래 카드: `{X}-FN`
- 사전 차원 결정 카드: `D-?N`

---

## 11. 스냅샷 / Soft reference 어휘

### 어휘 정의

| 어휘 | 정의 |
|------|------|
| **스냅샷 (snapshot)** | 주문 생성 시점의 product/brand 정보를 `order_item`에 복사하여 보존. 이후 원본 product/brand가 수정/삭제되어도 주문 기록은 불변 |
| **Soft reference** | FK 제약 없이 id만 보관 (예: `order_item.product_id`). product 삭제 시 cascade되지 않으며, 주문 기록 무결성에 영향 없음 |
| **Hard reference** | FK 제약 있는 정상 외래키 (예: `product.brand_id` → `brand(id)`) |
| **참조용 (FK 없음)** | ER 다이어그램 외부 도메인 표기 시 스냅샷/soft ref를 명시하는 표기 |

### 스냅샷 범위 (Order 도메인)

| 필드 | 보존 여부 | 이유 |
|------|----------|------|
| `productId` | soft ref | 식별만 |
| `productName` | **스냅샷** | 주문 시점 상품명 |
| `unitPrice` | **스냅샷** | 주문 시점 단가 |
| `quantity` | 직접 입력 | 주문 데이터 |
| `brandId`, `brandName` | **스냅샷** | Brand=Tenant 미래 집계용 |

> "스냅샷"이라는 단어는 `order_item` 컬럼 주석 / ER 다이어그램 / 시나리오 본문 모두 동일하게 사용한다.

---

## 12. Cascade 어휘

### 어휘 정의

| 어휘 | 정의 |
|------|------|
| **DB cascade** | `ON DELETE CASCADE` 같은 DB 레벨 자동 cascade |
| **Application 레벨 cascade** | Service 레이어에서 명시적으로 dependent 도메인 삭제를 호출 (예: `BrandService.delete` → `ProductService.deleteByBrand`) |
| **NO ACTION** | FK 제약은 있되 cascade 없음. 원본 삭제 시 dependent가 남아있으면 DB 레벨 거부 |

### 본 프로젝트 정책

| 관계 | 정책 | 이유 |
|------|------|------|
| Brand → Product | **Application 레벨 cascade** (DB는 NO ACTION) | 주문 스냅샷 등 다른 도메인 영향을 명시적으로 통제 |
| Order → OrderItem | **DB cascade** (`ON DELETE CASCADE`) | 같은 aggregate 내부, JPA cascade와 정합 |
| Product → ProductLike | (미정) | L-?2 / P-?6 결정에 의존 |

### 금지 어휘
- "cascade" 단독 사용 금지 — 항상 "DB cascade" 또는 "application 레벨 cascade" 명시
- "ON DELETE CASCADE를 application으로 처리" 같은 모순 표현 금지

---

## 13. ER 다이어그램 외부 도메인 표기

도메인 모델 섹션의 ER 박스에서 **다른 도메인 소유 테이블**을 참조할 때:

```
{table_name} (외부 도메인 — {filename}.html 참조)
```

예:
- Brand 문서에서 `product (외부 도메인 — 02-product-final.html 참조)`
- Likes 문서에서 `account (외부 도메인 — Account 도메인)` / `product (외부 도메인 — 02-product-final.html 참조)`

스냅샷 참조(FK 없음)는 별도 표기:
```
{table_name} (참조용 — FK 없음, 스냅샷)
```

---

## 14. 본인 검증 응답 정책 (403 vs 404)

타 사용자 리소스 접근 시 응답 status는 도메인 특성에 따라 분기한다 (의도된 차이).

| 도메인 | 응답 | 이유 |
|--------|------|------|
| **Like** (L-?5) | **403 FORBIDDEN** | 좋아요는 공개성 강함. 권한 없음을 명시. |
| **Order** (O-?7) | **404 NOT_FOUND** | 주문은 PII. ID enumeration 방어 (OWASP A01:2021). |

두 결정 카드 본문에 **서로를 cross-reference하며 차이 이유를 한 줄로 설명**한다.

---

## 15. 변경 이력

| 일자 | 변경 | 영향 산출물 |
|------|------|-------------|
| 2026-05-22 | 초안 작성. Week 2 4개 도메인 시나리오 HTML 표현 통일을 위한 출발점. | `docs/week2/scenarios/0[1-4]-*-final.html` |
| 2026-05-22 | §2 Role 어휘, §4 식별자 매핑 (userId↔accountId), §7 도메인 엔티티, §11 스냅샷, §12 cascade 추가. 필터 정식 클래스명 정렬. | 시나리오 4개 + big-picture.md + account-api-architecture.md |
| 2026-05-22 | 행위자 어휘 전면 개정 — "대고객/어드민" → "사용자/관리자". §9 약어 금지 규칙 추가 (Ctrl/Ctl/Svc/Repo/Mgr + Mermaid 단일 문자 alias). | 시나리오 4개 (md + html 양쪽) |
| 2026-05-22 | §7에 `Inventory` 엔티티 추가 + "도메인 책임 분리 (Product vs Inventory)" 섹션 신설. Product에서 stock 분리 결정 — 라이프사이클/정합성/캐시 전략 차이가 본질 근거. `product.stock` 컬럼 / `Product.stock` 필드 사용 금지. 재고 외부 노출은 derived (`inStock`/`stockStatus`) 만 허용. | 02-product (md/html/final), 04-orders (md/html/final) |

---

## 16. 본 사전이 정답이 아닌 영역

- **코드 레벨 컨벤션** (패키지명, 함수 시그니처, 테스트 명명 등) → `CLAUDE.md`, `AGENTS.md` 참조
- **비즈니스 정책** (재고 차감 시점, 스냅샷 범위 우선순위 등) → 결정 카드(`X-?N`) 및 `big-picture.md`
- **에러 핸들링 패턴 / 예외 클래스 사용 규칙** → `CLAUDE.md` §2 Error Handling
- **이 사전과 충돌하는 산출물**이 발견되면 사전이 옳다 — 산출물을 수정한다.
