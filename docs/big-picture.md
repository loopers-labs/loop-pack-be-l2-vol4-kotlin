# 루프팩 이커머스 — 최종 시나리오 (전체 큰 그림)

> 이 문서는 Loopers L2 vol4 과제의 **전체 최종 모습**을 보존하는 단일 진실 출처입니다.
> 매 주차 과제는 이 큰 그림의 일부를 구현하며, 결정이 변경되거나 큰 그림 자체가 갱신될 때 이 문서를 갱신합니다.
> 주차별 산출물은 `docs/weekN/` 아래에 별도로 둡니다.

---

## 🎯 배경

**좋아요** 누르고, **쿠폰** 쓰고, 카드로 **결제**하는 **감성 이커머스**.

내가 좋아하는 브랜드의 상품들을 한 번에 담아 주문하고, 유저 행동은 랭킹과 추천으로 연결돼요.

우린 이 흐름을 하나씩 직접 만들어갈 거예요.

---

## 🧭 서비스 흐름 예시

1. 사용자가 **회원가입**을 하고
2. 여러 브랜드의 상품을 둘러보고, 마음에 드는 상품엔 **좋아요**를 누르죠.
3. 사용자는 **쿠폰을 발급**받고, 여러 상품을 **한 번에 주문하고 결제**합니다.
4. 유저의 행동은 모두 기록되고, 그 데이터는 이후 다양한 기능으로 확장될 수 있어요.

---

## ✅ API 제안사항

- 대고객 기능은 `/api/v1` prefix 를 통해 제공합니다.

    ```markdown
    유저 로그인이 필요한 기능은 아래 헤더를 통해 유저를 식별해 제공합니다.
    인증/인가는 주요 스코프가 아니므로 구현하지 않습니다.
    유저는 타 유저의 정보에 직접 접근할 수 없습니다.

    * **X-Loopers-LoginId** : 로그인 ID
    * **X-Loopers-LoginPw** : 비밀번호
    ```

- 어드민 기능은 `/api-admin/v1`  prefix 를 통해 제공합니다.

    ```markdown
    어드민 기능은 아래 헤더를 통해 어드민을 식별해 제공합니다.

    * **X-Loopers-Ldap** : loopers.admin
    ```


## ✅ 요구사항

## 👤 유저 (Users)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me`  | O | 내 정보 조회 |
| PUT | `/api/v1/users/password`  | O | 비밀번호 변경 |

---

## 🏷 브랜드 & 상품 (Brands / Products)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회  |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

### ✅ 상품 목록 조회 쿼리 파라미터

| **파라미터** | **예시** | **설명** |
| --- | --- | --- |
| `brandId` | `1` | 특정 브랜드의 상품만 필터링 |
| `sort` | `latest` / `price_asc` / `likes_desc` | 정렬 기준 |
| `page` | `0` | 페이지 번호 (기본값 0) |
| `size` | `20` | 페이지당 상품 수 (기본값 20) |

> 💡 정렬 기준은 선택 구현입니다.
>
>
> 필수는 `latest`, 그 외는 `price_asc`, `likes_desc` 정도로 제한해도 충분합니다.
>

---

## 🏷 브랜드 & 상품 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands?page=0&size=20` | O | **등록된 브랜드 목록 조회** |
| GET | `/api-admin/v1/brands/{brandId}` | O | **브랜드 상세 조회** |
| POST | `/api-admin/v1/brands`  | O | **브랜드 등록** |
| PUT | `/api-admin/v1/brands/{brandId}`  | O | **브랜드 정보 수정** |
| DELETE | `/api-admin/v1/brands/{brandId}`  | O | **브랜드 삭제**
* 브랜드 제거 시, 해당 브랜드의 상품들도 삭제되어야 함 |
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | O | **등록된 상품 목록 조회** |
| GET | `/api-admin/v1/products/{productId}`  | O | **상품 상세 조회** |
| POST | `/api-admin/v1/products` | O | **상품 등록**
* 상품의 브랜드는 이미 등록된 브랜드여야 함 |
| PUT | `/api-admin/v1/products/{productId}`  | O | **상품 정보 수정**
* 상품의 브랜드는 수정할 수 없음 |
| DELETE | `/api-admin/v1/products/{productId}`  | O | **상품 삭제** |

> 상품, 브랜드 정보 중 고객과 어드민에게 제공되어야 할 정보에 대해 고민해보세요.
>

---

## ❤️ 좋아요 (Likes)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요 한 상품 목록 조회 |

---

## 🧾 주문 (Orders)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10`  | O | 유저의 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

**요청 예시:**

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

> **결제**는 과정 진행 중, **추가로 개발**하게 됩니다!
**주문 정보**에는 당시의 상품 정보가 스냅샷으로 저장되어야 합니다.
**주문 시에 다음 동작이 보장되어야 합니다 :** 상품 재고 확인 및 차감
>

---

## 🧾 주문 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders?page=0&size=20` | O | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

---

### 📡 나아가며

> ⚙️ **모든 기능의 동작을 개발한 후에 동시성, 멱등성, 일관성, 느린 조회, 동시 주문 등 실제 서비스에서 발생하는 문제들을 해결하게 됩니다.**
>

---

## 🧭 추가 비전 (과제 원문엔 없지만 본인이 정해둔 큰 그림)

이 섹션은 과제 원문의 명세를 넘어, 본인이 장기적으로 가져가고 싶은 시스템 방향을 정리한다.
이번 주 구현에는 들어가지 않지만, 설계가 미래에 어긋나지 않도록 의식적으로 마킹한다.

### 🏢 Brand = Tenant

- **Brand를 단순 "상품의 그룹"이 아니라 시스템의 입주 단위(Tenant)** 로 본다.
- 현재(Week 2)는 단일 Platform admin이 모든 Brand를 수기로 관리.
- 미래 가능성: 각 Brand가 자기 운영자(Brand staff)를 가지고, 자기 Brand 내 상품/주문/통계만 접근.
- 설계 영향: `brand_id`는 단순 FK가 아니라 **잠재적 권한 격리의 축**. brand 정보를 product 내부에 비정규화하는 식의 결정은 피한다.

### 👥 Role 분리

| Role | 권한 범위 | 도입 시점 |
|---|---|---|
| **Customer** | 본인 데이터(주문/좋아요)만 접근 | Week 1 완료 |
| **Platform Admin** | 모든 Brand/Product/Order CRUD | Week 2 (단일 계정 `loopers.admin`, 헤더 식별) |
| **Brand Owner / Staff** | 자기 Brand 내 데이터만 | 미래 — Tenant 분리와 함께 도입 |

- 이번 주는 Customer + Platform Admin 두 단계만. Brand staff는 명시적 미래 항목.
- 어드민 인증은 `X-Loopers-Ldap: loopers.admin` 정확 일치 검증으로 가볍게 — 실 LDAP/SSO 연동은 미래.

### 🛒 브랜드 등록 흐름 (운영 워크플로우)

- **Self-service 입점 없음.** Brand 입점 요청은 시스템 외부(이메일/구글폼 등) → 어드민이 수기로 `POST /api-admin/v1/brands`.
- 향후 Brand staff 도입 시 self-service 가능성 열어둠 (API는 같은 모양 유지하되 권한만 확장).

### 💳 결제 / 쿠폰 / 랭킹·추천

과제 원문의 미래 단계(`결제는 과정 진행 중, 추가로 개발`) + 시나리오의 큰 그림(쿠폰, 행동 데이터 → 랭킹/추천).
- **결제**: 외부 PG 연동, 멱등키, webhook → 주문 상태 모델 확장 필요
- **쿠폰**: 발급 → 적용 → 사용 흐름, 주문과의 결합
- **랭킹/추천**: 좋아요/주문 행동 데이터를 비동기 집계 → 정렬/추천 모델

이번 주는 모두 미설계. 다만 도메인 모델/ERD가 이 확장을 받아낼 수 있게.

---

## 📝 변경 로그

| 날짜 | 변경 내용 | 출처 |
| --- | --- | --- |
| 2026-05-21 | 초기 시나리오 문서 등록 (Week 2 설계 착수) | 과제 원본 |
| 2026-05-22 | "추가 비전" 섹션 추가 — Brand=Tenant, Role 분리, 수기 등록 워크플로우 명시 | 본인 결정 |
