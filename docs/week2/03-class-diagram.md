# 03. Class Diagram

`01-requirements.md`와 `02-sequence-diagrams.md`에서 정리한 요구사항과 핵심 흐름을 바탕으로, 주요 도메인 객체의 상태, 책임, 관계를 정리한다.

## 1. 작성 기준

- 클래스 내부에는 도메인 책임을 이해하는 데 필요한 핵심 상태만 포함한다.
- 객체 간 관계는 기본적으로 관계선으로 표현하고, 해당 객체의 핵심 책임을 설명하는 데 필요한 경우에만 연관 필드로 포함한다.
- 상속된 감사 필드는 ERD에서 다루고, 클래스 다이어그램에는 표기하지 않는다.
- 메서드는 도메인 규칙이나 상태 변화를 드러내는 행위만 포함한다.

## 2. 객체 구분

### 2.1 Entity

| 객체 | 역할 |
|---|---|
| `User` | 상품 좋아요와 주문을 수행하는 사용자 |
| `Brand` | 상품을 소유하거나 대표하는 브랜드 |
| `Product` | 사용자가 조회하고 주문할 수 있는 판매 상품 |
| `Like` | 사용자가 특정 상품에 표시한 관심 상태 |
| `Order` | 사용자의 주문 요청과 결제 이후 상태를 관리하는 주문 |
| `OrderItem` | 주문에 포함된 개별 상품 항목 |

### 2.2 Value Object

| 객체 | 역할 |
|---|---|
| `ProductSnapshot` | 주문 시점의 상품명과 가격을 보존한다. |

### 2.3 Enum

| 객체 | 역할 |
|---|---|
| `UserRole` | 사용자와 어드민 권한을 구분한다. |
| `OrderStatus` | 주문의 상태 전이를 표현한다. |

## 3. 클래스 다이어그램

### 3.1 상품/브랜드/좋아요 모델

상품은 브랜드에 속하고, 좋아요는 사용자와 상품 사이의 관심 상태를 표현한다.
`Product.likeCount`는 `likes_desc` 정렬을 위한 집계 값으로, `Like` 등록/취소와 함께 변경된다.
`Brand.softDelete()`는 하위 상품 연쇄 소프트 딜리트 정책의 출발점이다.

```mermaid
classDiagram
    direction TB

    class User {
        <<Entity>>
        String loginId
        UserRole role
    }

    class UserRole {
        <<Enumeration>>
        USER
        ADMIN
    }

    class Brand {
        <<Entity>>
        softDelete()
    }

    class Product {
        <<Entity>>
        Long price
        Int stock
        Int likeCount
        decreaseStock(quantity)
        restoreStock(quantity)
        isSoldOut() Boolean
        increaseLikeCount()
        decreaseLikeCount()
        softDelete()
    }

    class Like {
        <<Entity>>
        User user
        Product product
        cancel()
        restore()
    }

    Product "0..*" --> "1" Brand : 브랜드
    Like "0..*" --> "1" User : 사용자
    Like "0..*" --> "1" Product : 상품
    User --> UserRole : 권한
```

### 3.2 주문 모델

주문은 사용자, 주문 항목, 주문 상태를 중심으로 표현한다.
`OrderItem`은 주문 이후 상품 정보가 변경되더라도 주문 이력이 유지되도록 주문 시점의 상품 정보를 `ProductSnapshot`으로 보존한다.

```mermaid
classDiagram
    direction TB

    class User {
        <<Entity>>
        String loginId
        UserRole role
    }

    class UserRole {
        <<Enumeration>>
        USER
        ADMIN
    }

    class Order {
        <<Entity>>
        List~OrderItem~ items
        OrderStatus status
        Long totalAmount
        addItem(productSnapshot, quantity)
        calculateTotalAmount()
        markPaid()
        markPaymentFailed()
    }

    class OrderItem {
        <<Entity>>
        ProductSnapshot productSnapshot
        Int quantity
        calculateAmount()
    }

    class Product {
        <<Entity>>
    }

    class OrderStatus {
        <<Enumeration>>
        PENDING_PAYMENT
        PAID
        PAYMENT_FAILED
    }

    Order "0..*" --> "1" User : 주문자
    Order "1" *-- "1..*" OrderItem : 포함
    OrderItem ..> Product : 주문 시점 참조
    User --> UserRole : 권한
    Order --> OrderStatus : 주문 상태
```
