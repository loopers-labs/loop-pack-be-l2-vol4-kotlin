# 04. ERD

`01-requirements.md`, `02-sequence-diagrams.md`, `03-class-diagram.md`를 바탕으로 이커머스 도메인의 전체 테이블 구조와 관계를 정리한다.

## 1. 설계 결정

- 모든 테이블은 `BaseEntity`를 상속하므로 `id`, `created_at`, `updated_at`, `deleted_at` 컬럼을 공통으로 가진다.
- DB 레벨 FK 제약은 사용하지 않고, 참조 무결성은 애플리케이션 레이어에서 보장한다.
- 참조 ID 컬럼에는 조회 성능을 위해 인덱스를 건다.
- `users.password`는 인코딩된 비밀번호를 저장하며, 평문 비밀번호는 저장하지 않는다.
- 사용자는 `role` 컬럼으로 일반 사용자와 어드민을 구분하며, 별도 권한 테이블은 두지 않는다.
- soft delete는 공통 `deleted_at` 컬럼으로 표현한다.
- 좋아요는 `(user_id, product_id)` 조합의 중복을 허용하지 않는다.
- `likes`는 취소 후 재등록 시 기존 레코드의 `deleted_at`을 null로 복구한다.
- 주문 상품은 원본 상품 ID와 주문 시점 상품 스냅샷을 함께 저장한다.
- 결제 이력 테이블은 두지 않고, 외부 결제 결과는 주문 상태에 반영한다.
- 주문 상태는 현재 설계 범위에서 `PENDING_PAYMENT`, `PAID`, `PAYMENT_FAILED`만 사용하며, 별도 상태 테이블은 두지 않는다.

## 2. ERD

아래 관계선은 DB 레벨 FK 제약이 아니라, 애플리케이션에서 관리하는 논리적 참조 관계를 의미한다.

```mermaid
erDiagram
    users {
        BIGINT id PK
        VARCHAR login_id UK
        VARCHAR password
        VARCHAR name
        DATE birth_date
        VARCHAR email
        VARCHAR role
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    brands {
        BIGINT id PK
        VARCHAR name
        TEXT description
        VARCHAR logo_image_url
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    products {
        BIGINT id PK
        BIGINT brand_id "INDEX"
        VARCHAR name
        BIGINT price
        INT stock
        INT like_count
        TEXT description
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    likes {
        BIGINT id PK
        BIGINT user_id "INDEX, COMPOSITE_UK"
        BIGINT product_id "INDEX, COMPOSITE_UK"
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    orders {
        BIGINT id PK
        BIGINT user_id "INDEX"
        VARCHAR status
        BIGINT total_amount
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    order_items {
        BIGINT id PK
        BIGINT order_id "INDEX"
        BIGINT product_id "INDEX"
        VARCHAR product_name
        BIGINT product_price
        INT quantity
        DATETIME created_at
        DATETIME updated_at
        DATETIME deleted_at
    }

    brands ||--o{ products : ""
    users ||--o{ likes : ""
    products ||--o{ likes : ""
    users ||--o{ orders : ""
    orders ||--|{ order_items : ""
    products ||--o{ order_items : ""
```
