# 03. 클래스 다이어그램

이 문서는 현재 모놀리식 구현의 도메인 객체와 Aggregate 경계를 정의한다. Bounded Context는 물리 서비스가 아니라 같은 서버 안의 패키지/모듈 경계다.

## 1. Catalog Context

목적: 상품, 브랜드, 재고, 좋아요 집계 수가 Catalog Context의 책임임을 표현한다.

```mermaid
classDiagram
    class Seller {
        -SellerId id
        -String name
        -SellerStatus status
        +activate()
        +suspend()
    }

    class Brand {
        -BrandId id
        -SellerId sellerId
        -String name
        -BrandStatus status
        +activate()
        +deactivate()
    }

    class Product {
        -ProductId id
        -BrandId brandId
        -String name
        -Money price
        -ProductStatus status
        -int stockQuantity
        -long likeCount
        -long version
        +changeName(String name)
        +changePrice(Money price)
        +reduceStock(int quantity) ProductStockChanged
        +restoreStock(int quantity) ProductStockChanged
        +increaseLikeCount() void
        +decreaseLikeCount() void
        +isOrderable(int quantity) boolean
    }

    class Money {
        -BigDecimal amount
        -String currency
        +plus(Money other) Money
        +times(int quantity) Money
    }

    class ProductStatus {
        <<enumeration>>
        ON_SALE
        OUT_OF_STOCK
        SUSPENDED
    }

    class SellerStatus {
        <<enumeration>>
        ACTIVE
        SUSPENDED
    }

    class BrandStatus {
        <<enumeration>>
        ACTIVE
        INACTIVE
    }

    class BrandCreated {
        -EventId eventId
        -SellerId sellerId
        -BrandId brandId
        -LocalDateTime occurredAt
    }

    class ProductCreated {
        -EventId eventId
        -SellerId sellerId
        -BrandId brandId
        -ProductId productId
        -LocalDateTime occurredAt
    }

    class ProductStockChanged {
        -EventId eventId
        -ProductId productId
        -int delta
        -int stockQuantity
        -String reason
        -LocalDateTime occurredAt
    }

    Seller "1" --> "many" Brand : manages
    Brand "1" --> "many" Product : owns
    Seller ..> SellerStatus : has
    Brand ..> BrandStatus : has
    Product ..> Money : uses
    Product ..> ProductStatus : has
    Brand ..> BrandCreated : emits
    Product ..> ProductCreated : emits
    Product ..> ProductStockChanged : emits on stock change
```

읽는 법:

1. `Seller`는 브랜드와 상품 운영 권한의 기준이다.
2. `Product`는 상품 재고와 좋아요 집계 수의 변경 규칙을 가진다.
3. `likeCount`는 단순 조회 최적화를 위한 현재 집계 값이며 `ProductLikeHistory`를 대체하지 않는다.
4. 재고 변경은 Seller의 수동 조정, 주문, 취소 유스케이스에서 호출되지만, 수량 불변식은 `Product`가 보호한다.

## 2. Preference Context

목적: Like/Unlike의 현재 상태와 이력이 분리되어야 멱등성과 고객 행동 이력을 동시에 만족한다는 점을 표현한다.

```mermaid
classDiagram
    class ProductLikeState {
        -ConsumerId consumerId
        -ProductId productId
        -boolean liked
        -LocalDateTime updatedAt
        +like() LikeTransitionResult
        +unlike() LikeTransitionResult
        +isLiked() boolean
    }

    class LikeTransitionResult {
        -boolean changed
        -DomainEvent event
    }

    class ProductLikeHistory {
        -ProductLikeId id
        -ConsumerId consumerId
        -ProductId productId
        -LikeAction action
        -LocalDateTime occurredAt
        +recordLike(ConsumerId consumerId, ProductId productId) ProductLikeHistory
        +recordUnlike(ConsumerId consumerId, ProductId productId) ProductLikeHistory
    }

    class LikeAction {
        <<enumeration>>
        LIKE
        UNLIKE
    }

    class ProductLiked {
        -EventId eventId
        -ConsumerId consumerId
        -ProductId productId
        -LocalDateTime occurredAt
    }

    class ProductUnliked {
        -EventId eventId
        -ConsumerId consumerId
        -ProductId productId
        -LocalDateTime occurredAt
    }

    ProductLikeState ..> ProductLiked : emits on transition
    ProductLikeState ..> ProductUnliked : emits on transition
    ProductLikeState ..> LikeTransitionResult : returns
    ProductLikeHistory ..> LikeAction : has
```

읽는 법:

1. `ProductLikeHistory`는 고객의 Like/Unlike 이력이다. 상태 전환이 발생할 때만 append한다.
2. `ProductLikeState`는 `(consumerId, productId)`의 현재 상태다. 중복 요청은 이벤트를 반환하지 않는다.
3. Application Service는 상태 전환 이벤트가 존재할 때만 `Product.likeCount`를 갱신하고 `outbox_events`에 저장한다.

## 3. Ordering Context

목적: 주문은 삭제되지 않는 고객 주문 이력이며, 취소는 상태 전이와 이력으로 남는다는 점을 표현한다.

```mermaid
classDiagram
    class Order {
        -OrderId id
        -ConsumerId consumerId
        -List~OrderLineItem~ lineItems
        -Money totalAmount
        -OrderStatus status
        -LocalDateTime orderedAt
        -LocalDateTime cancelledAt
        -String cancelReason
        +place(ConsumerId consumerId, List~OrderLineItem~ items) OrderPlaced
        +cancel(String reason) OrderCancelResult
        +isCancellable() boolean
    }

    class OrderCancelResult {
        -boolean cancelled
        -OrderCancelled event
    }

    class OrderLineItem {
        -Long id
        -ProductId productId
        -String productName
        -Money unitPrice
        -int quantity
        +subtotal() Money
    }

    class OrderStatusHistory {
        -Long id
        -OrderId orderId
        -OrderStatus status
        -String reason
        -LocalDateTime occurredAt
        +record(OrderId orderId, OrderStatus status, String reason) OrderStatusHistory
    }

    class OrderStatus {
        <<enumeration>>
        ORDERED
        CANCELLED
    }

    class OrderPlaced {
        -EventId eventId
        -OrderId orderId
        -ConsumerId consumerId
        -Money totalAmount
        -LocalDateTime occurredAt
    }

    class OrderCancelled {
        -EventId eventId
        -OrderId orderId
        -ConsumerId consumerId
        -String cancelReason
        -LocalDateTime occurredAt
    }

    Order "1" *-- "many" OrderLineItem : contains
    Order "1" --> "many" OrderStatusHistory : records
    Order ..> OrderPlaced : emits
    Order ..> OrderCancelled : emits on transition
    Order ..> OrderCancelResult : returns on cancel
```

읽는 법:

1. `Order`는 Aggregate Root다.
2. `OrderLineItem`은 주문 시점의 상품 스냅샷을 가진다.
3. 이미 취소된 주문의 `cancel()`은 새 이벤트를 만들지 않아야 한다.
4. 주문 취소는 주문 삭제가 아니라 `CANCELLED` 상태 전이다.

## 4. Domain Event와 Outbox

목적: Domain Event가 현재 모놀리식 내부의 핵심 통합 방식이며, `OutboxEvent`는 재시도를 위한 영속 레코드임을 표현한다.

```mermaid
classDiagram
    class DomainEvent {
        <<interface>>
        +eventId() EventId
        +eventType() String
        +aggregateType() String
        +aggregateId() String
        +occurredAt() LocalDateTime
    }

    class OutboxEvent {
        -EventId eventId
        -String aggregateType
        -String aggregateId
        -String eventType
        -String payload
        -OutboxStatus status
        -int retryCount
        -LocalDateTime occurredAt
        -LocalDateTime processedAt
        -String lastError
        +markProcessed()
        +markFailed(String reason)
    }

    class OutboxStatus {
        <<enumeration>>
        PENDING
        PROCESSED
        FAILED
    }

    class DomainEventRecorder {
        +record(DomainEvent event) OutboxEvent
    }

    class OutboxWorker {
        +dispatchPendingEvents()
    }

    DomainEventRecorder ..> DomainEvent : receives
    DomainEventRecorder ..> OutboxEvent : persists
    OutboxWorker ..> OutboxEvent : retries
    OutboxEvent ..> OutboxStatus : has
```

읽는 법:

1. Aggregate는 Domain Event를 생성한다.
2. Application Service는 Aggregate 변경과 `OutboxEvent` 저장을 같은 DB 트랜잭션에 포함한다.
3. `OutboxWorker`는 같은 서버 프로세스 안에서 실패한 로컬 이벤트 처리를 재시도한다.
4. 현재 설계에는 외부 브로커 생산자, 외부 소비자, 미래 분석 Context 핸들러가 없다.
