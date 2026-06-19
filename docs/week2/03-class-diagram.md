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
| `Stock` | 상품별 주문 가능 재고 수량 |
| `Like` | 사용자가 특정 상품에 표시한 관심 상태 |
| `ProductLikeCountProjection` | 상품별 좋아요 수를 비정규화한 CQRS 프로젝션 |
| `Coupon` | 정액/정률 할인 정책을 가진 쿠폰 원본 |
| `UserCoupon` | 사용자가 보유한 발급 쿠폰 1장 |
| `Order` | 사용자의 주문 요청과 결제 이후 상태를 관리하는 주문 |
| `OrderItem` | 주문에 포함된 개별 상품 항목 |

### 2.2 Value Object

| 객체 | 역할 |
|---|---|
| `ProductPrice` | 상품 가격을 표현하고 0 이상이라는 규칙을 보장한다. |
| `ProductSnapshot` | 주문 시점의 상품명과 가격을 보존한다. |
| `OrderQuantity` | 주문 상품 수량을 표현하고 1개 이상이라는 규칙을 보장한다. |
| `OrderItemPrice` | 주문 상품 1건의 금액을 표현한다. |
| `OrderAmount` | 주문의 할인 전 금액, 할인 금액, 결제 금액을 표현하는 금액 값이다. |
| `OrderAmounts` | `totalAmount - discountAmount = paymentAmount` 불변식을 보장한다. |
| `DiscountAmount` | 쿠폰 정책이 계산한 할인 금액을 표현한다. |

### 2.3 Enum

| 객체 | 역할 |
|---|---|
| `UserRole` | 사용자와 어드민 권한을 구분한다. |
| `OrderStatus` | 주문의 상태 전이를 표현한다. |
| `DiscountPolicy.Type` | 쿠폰 할인 정책의 정액/정률 종류를 구분한다. |
| `PaymentResult` | 외부 결제 승인 결과를 표현한다. |

## 3. 클래스 다이어그램

### 3.1 상품/브랜드/좋아요 모델

상품은 브랜드에 속하고, 재고는 상품 ID를 기준으로 별도 애그리거트에서 관리한다.
좋아요는 사용자와 상품 사이의 관심 상태를 표현한다.
좋아요 수는 `product_like_counts` 프로젝션 테이블에서 관리하며, `LikeCountSyncJob` 배치가 주기적으로 동기화한다. `likes_desc` 정렬은 이 프로젝션 테이블을 기준으로 수행한다.
`Brand.softDelete()`는 하위 상품 연쇄 삭제 정책의 출발점이다.

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
        Long brandId
        ProductPrice price
        rename(name)
        changeDescription(description)
        changePrice(price)
    }

    class ProductPrice {
        <<Value Object>>
        Long amount
    }

    class ProductLikeCountProjection {
        <<Projection>>
        Long productId
        Long brandId
        Int likeCount
    }

    class Stock {
        <<Entity>>
        Long productId
        Int quantity
        validateDeductible(amount)
        isSoldOut() Boolean
        softDelete()
    }

    class Like {
        <<Entity>>
        Long userId
        Long productId
        restore()
        cancel()
    }

    Product "0..*" --> "1" Brand : 브랜드
    Product --> ProductPrice : 가격
    Stock "1" --> "1" Product : 재고 대상
    ProductLikeCountProjection "1" --> "1" Product : 좋아요 집계
    Like "0..*" --> "1" User : 사용자
    Like "0..*" --> "1" Product : 상품
    User --> UserRole : 권한
```

### 3.2 주문 모델

주문은 사용자, 사용한 발급 쿠폰, 주문 항목, 주문 상태, 금액 스냅샷을 중심으로 표현한다.
`OrderItem`은 주문 이후 상품 정보가 변경되더라도 주문 이력이 유지되도록 주문 시점의 상품 정보를 `ProductSnapshot`으로 보존한다.
`OrderAmounts`는 할인 전 상품 총액, 할인 금액, 최종 결제 금액 사이의 계산 불변식을 담당한다.

쿠폰은 정책 원본인 `Coupon`과 발급 쿠폰 1장을 의미하는 `UserCoupon`으로 분리한다.
주문에서 쿠폰을 사용할 때는 `Coupon.id`가 아니라 `UserCoupon.id`로 특정 발급분을 식별한다.
외부 결제는 DB 트랜잭션 밖에서 `PaymentGateway`를 통해 호출하고, 결제 성공 후 주문 확정이 실패하면 결제 취소를 요청한다.
주문 상태 전이는 동시성 경합을 고려해 `PENDING_PAYMENT` 조건부 UPDATE로 원자화한다.

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
        Long userId
        Long userCouponId
        List~OrderItem~ items
        OrderStatus status
        OrderAmount totalAmount
        OrderAmount discountAmount
        OrderAmount paymentAmount
        markPaid()
        markPaymentFailed()
        cancel()
    }

    class OrderItem {
        <<Entity>>
        ProductSnapshot productSnapshot
        OrderQuantity quantity
        OrderItemPrice totalPrice
    }

    class Product {
        <<Entity>>
    }

    class Coupon {
        <<Entity>>
        String name
        DiscountPolicy policy
        discountOf(targetAmount) DiscountAmount
    }

    class UserCoupon {
        <<Entity>>
        Long userId
        Long couponId
        LocalDateTime usedAt
        isUsed() Boolean
        validateOwnedBy(userId)
        validateUsable()
    }

    class DiscountPolicy {
        <<Sealed>>
        discountOf(targetAmount) DiscountAmount
    }

    class OrderAmount {
        <<Value Object>>
        Long amount
    }

    class OrderAmounts {
        <<Value Object>>
        OrderAmount totalAmount
        OrderAmount discountAmount
        OrderAmount paymentAmount
    }

    class OrderAmountCalculator {
        <<Domain Service>>
        calculate(items, coupon) OrderAmounts
    }

    class PaymentGateway {
        <<Port>>
        pay(command) PaymentResult
        cancel(command)
    }

    class PaymentResult {
        <<Enumeration>>
        SUCCESS
        FAILED
    }

    class OrderConfirmResult {
        <<Sealed>>
        Confirmed
        AlreadyPaid
        AlreadyTerminated
    }

    class OrderStatus {
        <<Enumeration>>
        PENDING_PAYMENT
        PAID
        PAYMENT_FAILED
        CANCELED
    }

    Order "0..*" --> "1" User : 주문자
    Order "1" *-- "1..*" OrderItem : 포함
    Order --> OrderAmounts : 금액 스냅샷
    OrderItem ..> Product : 주문 시점 참조
    UserCoupon "0..*" --> "1" User : 보유자
    UserCoupon "0..*" --> "1" Coupon : 쿠폰 원본
    Order ..> UserCoupon : 사용 발급분
    OrderAmountCalculator ..> OrderItem : 주문 상품
    OrderAmountCalculator ..> Coupon : 할인 정책
    OrderConfirmResult ..> Order : 확정 결과
    PaymentGateway ..> PaymentResult : 결제 결과
    Coupon --> DiscountPolicy : 할인 정책
    User --> UserRole : 권한
    Order --> OrderStatus : 주문 상태
```
