# Sequence 01 - 주문 생성

## 1. Why

주문 생성은 이번 범위에서 정합성 위험이 가장 큰 흐름이다.  
재고 확인, 재고 차감, 주문 스냅샷 저장이 하나의 책임 흐름으로 맞물려야 하므로, 트랜잭션 경계와 실패 지점을 먼저 드러내야 한다.

## 2. Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant OrderController as OrderController
    participant OrderAppService as OrderCommandApplicationService
    participant OrderDomainService as OrderPlacementDomainService
    participant ProductRepo as ProductRepository
    participant InventoryRepo as InventoryRepository
    participant OrderService as OrderService
    participant Order as Order
    participant OrderRepo as OrderRepository

    User->>OrderController: POST /api/v1/orders
    OrderController->>OrderAppService: placeOrder(memberId, items)
    Note over OrderAppService,OrderRepo: 하나의 주문 트랜잭션
    OrderAppService->>ProductRepo: findAllByIds(productIds)
    ProductRepo-->>OrderAppService: products
    OrderAppService->>InventoryRepo: findAllByProductIdsForUpdate(productIds)
    InventoryRepo-->>OrderAppService: inventories
    OrderAppService->>OrderDomainService: place(memberId, items, products, inventories)

    alt 하나라도 상품 없음 또는 재고 부족
        OrderDomainService-->>OrderAppService: 주문 실패
        OrderAppService-->>OrderController: 주문 실패
        OrderController-->>User: 실패 응답
    else 전체 검증 성공
        OrderDomainService-->>OrderAppService: itemSnapshots and updated inventories
        OrderAppService->>OrderService: createCompletedOrder(memberId, itemSnapshots)
        OrderService->>Order: createCompleted(memberId, itemSnapshots)
        Order-->>OrderService: completed Order
        Note right of Order: Order 생성 시 OrderItem도 함께 구성
        OrderService->>OrderRepo: save(order with orderItems)
        OrderRepo-->>OrderService: saved order
        Note right of OrderService: 미래 확장 포인트\nOrderCompleted 이벤트 발행 가능
        OrderService-->>OrderAppService: 주문 완료 결과
        OrderAppService-->>OrderController: 주문 완료 결과
        OrderController-->>User: 성공 응답
    end
```

## 3. Key Points

- 주문 생성의 성공 조건은 "모든 항목 재고 확인 성공"이다. 한 항목이라도 부족하면 전체 주문이 실패한다.
- `OrderCommandApplicationService`가 repository interface 를 통해 상품과 재고를 조회하고 전체 트랜잭션 흐름을 조정한다.
- 상품 존재 여부 확인, 재고 검증, 재고 차감, 주문 스냅샷 생성 같은 도메인 협력은 `OrderPlacementDomainService`가 담당한다.
- `OrderService`는 준비된 주문 스냅샷으로 `Order` aggregate 를 생성하고 저장 흐름으로 넘긴다.
- 재고 차감과 주문 저장은 같은 트랜잭션 경계 안에 있어야 한다. 그래야 주문 저장만 되고 재고 차감이 누락되는 상태를 막을 수 있다.
- `OrderItem`은 `Order` aggregate 내부 구성요소로 함께 생성되고 저장된다. JPA 설계에서는 보통 `Order` 저장 시 cascade 또는 aggregate 저장 규칙으로 같이 반영된다고 해석한다.
- 결제/외부 연동은 현재 흐름에 넣지 않되, 주문 완료 이후 이벤트나 포트로 확장할 수 있게 남겨둔다.
