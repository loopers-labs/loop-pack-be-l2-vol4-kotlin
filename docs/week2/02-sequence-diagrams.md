# 02. Sequence Diagrams

`01-requirements.md`의 기능 요구사항 중 상태 변경, 멱등성, 연쇄 처리, 외부 시스템 연동이 드러나는 핵심 유스케이스를 시퀀스 다이어그램으로 작성한다.

## 1. 작성 기준

- 각 유스케이스는 도메인 중심 흐름과 구현을 위한 내부 클래스 흐름 두 가지 관점으로 작성한다.
  - 도메인 중심 흐름은 비개발자도 이해할 수 있는 책임과 상태 변화를 중심으로 표현한다.
  - 내부 클래스 흐름은 개발자가 구현 책임과 호출 순서를 파악할 수 있도록 Controller, Facade, Service, Repository 단위로 표현한다.

## 2. 다이어그램 목록

| 흐름 | 핵심 관점 |
|---|---|
| 주문 생성 및 결제 | 재고 차감, 주문 스냅샷, 외부 결제, 실패 보상이 함께 발생하는 핵심 흐름 |
| 상품 좋아요 등록/취소 | 멱등성 정책이 중요하며, 기존 좋아요 존재 여부에 따라 분기가 발생하는 흐름 |
| 브랜드 삭제 및 상품 연쇄 삭제 | 브랜드 삭제 시 하위 상품도 함께 소프트 딜리트되는 연쇄 정책 흐름 |

## 3. 주문 생성 및 결제 흐름

### 3.1 개요

주문 생성 및 결제 흐름은 재고 확보, 주문 생성, 외부 결제 연동, 실패 보상이 한 유스케이스 안에서 이어지는 흐름이다.
이 다이어그램에서는 외부 결제를 DB 트랜잭션과 분리하고, 결제 결과에 따라 주문 상태와 재고를 후속 처리하는 구조를 중심으로 표현한다.

### 3.2 도메인 중심 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Auth as 사용자 인증
    participant Order as 주문
    participant Product as 상품/재고
    participant Payment as 외부 결제 시스템

    User->>Order: 주문 요청<br/>(상품 ID, 수량 목록)
    Order->>Auth: 사용자 식별
    Auth-->>Order: 사용자 정보
    Order->>Product: 상품 존재 여부와 재고 확인

    alt 주문 가능
        Product-->>Order: 주문 가능
        Order->>Product: 재고 차감
        Product-->>Order: 재고 차감 완료
        Order->>Order: 주문 생성<br/>(PENDING_PAYMENT, 상품 스냅샷)
        Order->>Payment: 결제 승인 요청
        Payment-->>Order: 결제 승인 결과

        alt 결제 성공
            Order->>Order: 주문 상태를 PAID로 변경
            Order-->>User: 201 Created<br/>(주문 정보)
        else 결제 실패
            Order->>Product: 재고 차감 취소
            Product-->>Order: 재고 차감 취소 완료
            Order->>Order: 주문 실패 처리<br/>(PAYMENT_FAILED)
            Order-->>User: 결제 실패 응답
        end
    else 주문 불가
        Product-->>Order: 상품 없음 또는 재고 부족
        Order-->>User: 주문 실패
    end
```

### 3.3 내부 클래스 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as OrderV1Controller
    participant Facade as OrderFacade
    participant UserService as UserService
    participant ProductService as ProductService
    participant OrderService as OrderService
    participant Order as Order
    participant PaymentGateway as PaymentGateway
    participant OrderRepository as OrderRepository

    User->>Controller: 주문 요청<br/>(상품 ID, 수량 목록, 로그인 헤더)
    Controller->>Facade: createOrder(loginUser, items)
    activate Facade

    Facade->>UserService: getUser(loginUser.loginId, loginUser.rawPassword)
    activate UserService
    UserService-->>Facade: 사용자 정보
    deactivate UserService

    Facade->>ProductService: getOrderableProducts(items)
    activate ProductService
    ProductService-->>Facade: 주문 가능 여부
    deactivate ProductService

    alt 주문 불가
        Facade-->>Controller: 주문 생성 실패 결과
        Controller-->>User: 주문 생성 실패 응답
    else 주문 가능
        rect rgb(235, 245, 255)
            Note over Facade,OrderService: TX1 - 재고 차감 및 주문 생성
            Facade->>ProductService: decreaseStock(items)
            activate ProductService
            ProductService-->>Facade: 재고 차감 완료
            deactivate ProductService

            Facade->>OrderService: createOrder(user, products)
            activate OrderService
            OrderService->>OrderService: 주문 상품 스냅샷 생성
            OrderService->>OrderRepository: save(order)
            activate OrderRepository
            OrderRepository-->>OrderService: 저장된 주문
            deactivate OrderRepository
            OrderService-->>Facade: 주문 정보
            deactivate OrderService
        end

        rect rgb(245, 245, 245)
            Note over Facade,PaymentGateway: 외부 결제 호출 - DB 트랜잭션 밖
            Facade->>PaymentGateway: approve(orderId, totalAmount)
            activate PaymentGateway
            PaymentGateway-->>Facade: 결제 승인 결과
            deactivate PaymentGateway
        end

        alt 결제 성공
            rect rgb(235, 255, 235)
                Note over Facade,OrderService: TX2 - 주문 결제 완료 처리
                Facade->>OrderService: markPaid(orderId)
                activate OrderService
                OrderService->>Order: markPaid()
                activate Order
                Order->>Order: status = PAID
                deactivate Order
                OrderService->>OrderRepository: save(order)
                activate OrderRepository
                OrderRepository-->>OrderService: 저장 완료
                deactivate OrderRepository
                OrderService-->>Facade: 결제 완료 주문 정보
                deactivate OrderService
            end
            Facade-->>Controller: 주문 생성 결과
            Controller-->>User: 201 Created<br/>(주문 정보)
        else 결제 실패 또는 외부 연동 실패
            rect rgb(255, 240, 240)
                Note over Facade,OrderService: TX3 - 재고 복구 및 주문 실패 처리
                Facade->>ProductService: restoreStock(items)
                activate ProductService
                ProductService-->>Facade: 재고 복구 완료
                deactivate ProductService
                Facade->>OrderService: markPaymentFailed(orderId)
                activate OrderService
                OrderService->>Order: markPaymentFailed()
                activate Order
                Order->>Order: status = PAYMENT_FAILED
                deactivate Order
                OrderService->>OrderRepository: save(order)
                activate OrderRepository
                OrderRepository-->>OrderService: 저장 완료
                deactivate OrderRepository
                OrderService-->>Facade: 주문 실패 처리 완료
                deactivate OrderService
            end
            Facade-->>Controller: 주문 생성 실패 결과
            Controller-->>User: 결제 실패 응답
        end
    end
    deactivate Facade
```

## 4. 상품 좋아요 등록/취소 흐름

### 4.1 개요

상품 좋아요 등록/취소 흐름은 같은 요청이 반복되어도 결과가 안정적으로 유지되는 멱등성 정책을 중심으로 작성했다. 도메인 중심 흐름은 등록/취소 요청의 큰 흐름만 표현하고, 기존 좋아요 상태에 따른 생성, 복구, 취소 처리, no-op 분기는 내부 클래스 흐름에서 표현한다.

### 4.2 도메인 중심 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Auth as 사용자 인증
    participant Like as 좋아요
    participant Product as 상품

    User->>Like: 좋아요 등록 또는 취소 요청
    Like->>Auth: 사용자 식별
    Auth-->>Like: 사용자 정보
    Like->>Product: 상품 존재 여부 확인
    Product-->>Like: 상품 확인 결과

    alt 상품이 존재하지 않음
        Like-->>User: 요청 실패
    else 상품이 존재함
        alt 좋아요 등록 요청
            Like->>Like: 좋아요 등록 처리
            Like-->>User: 200 OK
        else 좋아요 취소 요청
            Like->>Like: 좋아요 취소 처리
            Like-->>User: 200 OK
        end
    end
```

### 4.3 좋아요 등록 내부 클래스 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as LikeV1Controller
    participant Facade as LikeFacade
    participant UserService as UserService
    participant ProductService as ProductService
    participant LikeService as LikeService
    participant Like as Like
    participant LikeRepository as LikeRepository

    User->>Controller: POST /api/v1/products/{productId}/likes<br/>(로그인 헤더)
    Controller->>Facade: addLike(loginUser, productId)
    activate Facade

    Facade->>UserService: getUser(loginUser.loginId, loginUser.rawPassword)
    activate UserService
    UserService-->>Facade: 사용자 정보
    deactivate UserService

    Facade->>ProductService: getProduct(productId)
    activate ProductService
    ProductService-->>Facade: 상품 정보
    deactivate ProductService

    alt 상품이 존재하지 않음
        Facade-->>Controller: 상품 없음
        Controller-->>User: 실패 응답
    else 상품이 존재함
        Facade->>LikeService: addLike(user, product)
        activate LikeService
        LikeService->>LikeRepository: findByUserAndProduct(user, product)
        activate LikeRepository
        LikeRepository-->>LikeService: 좋아요 조회 결과
        deactivate LikeRepository

        alt 좋아요 없음
            rect rgb(235, 245, 255)
                Note over LikeService,ProductService: TX - 좋아요 생성 및 좋아요 수 증가
                LikeService->>LikeRepository: save(new Like)
                activate LikeRepository
                LikeRepository-->>LikeService: 저장 완료
                deactivate LikeRepository
                LikeService->>ProductService: increaseLikeCount(product.id)
            end
        else 취소된 좋아요 있음
            rect rgb(235, 245, 255)
                Note over LikeService,ProductService: TX - 좋아요 복구 및 좋아요 수 증가
                LikeService->>Like: restore()
                activate Like
                Like-->>LikeService: 복구 반영
                deactivate Like
                LikeService->>LikeRepository: save(like)
                activate LikeRepository
                LikeRepository-->>LikeService: 저장 완료
                deactivate LikeRepository
                LikeService->>ProductService: increaseLikeCount(product.id)
            end
        else 활성 좋아요 있음
            LikeService->>LikeService: no-op
        end

        LikeService-->>Facade: 처리 결과
        deactivate LikeService
        Facade-->>Controller: 처리 결과
        Controller-->>User: 200 OK
    end
    deactivate Facade
```

### 4.4 좋아요 취소 내부 클래스 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Controller as LikeV1Controller
    participant Facade as LikeFacade
    participant UserService as UserService
    participant ProductService as ProductService
    participant LikeService as LikeService
    participant Like as Like
    participant LikeRepository as LikeRepository

    User->>Controller: DELETE /api/v1/products/{productId}/likes<br/>(로그인 헤더)
    Controller->>Facade: cancelLike(loginUser, productId)
    activate Facade

    Facade->>UserService: getUser(loginUser.loginId, loginUser.rawPassword)
    activate UserService
    UserService-->>Facade: 사용자 정보
    deactivate UserService

    Facade->>ProductService: getProduct(productId)
    activate ProductService
    ProductService-->>Facade: 상품 정보
    deactivate ProductService

    alt 상품이 존재하지 않음
        Facade-->>Controller: 상품 없음
        Controller-->>User: 실패 응답
    else 상품이 존재함
        Facade->>LikeService: cancelLike(user, product)
        activate LikeService
        LikeService->>LikeRepository: findByUserAndProduct(user, product)
        activate LikeRepository
        LikeRepository-->>LikeService: 좋아요 조회 결과
        deactivate LikeRepository

        alt 활성 좋아요 있음
            rect rgb(235, 245, 255)
                Note over LikeService,ProductService: TX - 좋아요 취소 및 좋아요 수 감소
                LikeService->>Like: cancel()
                activate Like
                Like-->>LikeService: 취소 반영
                deactivate Like
                LikeService->>LikeRepository: save(like)
                activate LikeRepository
                LikeRepository-->>LikeService: 저장 완료
                deactivate LikeRepository
                LikeService->>ProductService: decreaseLikeCount(product.id)
            end
        else 좋아요 없음 또는 이미 취소됨
            LikeService->>LikeService: no-op
        end

        LikeService-->>Facade: 처리 결과
        deactivate LikeService
        Facade-->>Controller: 처리 결과
        Controller-->>User: 200 OK
    end
    deactivate Facade
```

## 5. 브랜드 삭제 및 상품 연쇄 삭제 

### 5.1 개요

브랜드 삭제는 브랜드 자체 삭제뿐 아니라 하위 상품 전체를 함께 소프트 딜리트하는 연쇄 정책을 포함한다.

도메인 중심 흐름은 사용자 식별과 어드민 권한 확인, 브랜드 존재 확인, 하위 상품 삭제, 브랜드 삭제의 큰 순서를 표현한다.
내부 클래스 흐름은 하위 상품 벌크 소프트 딜리트와 브랜드 소프트 딜리트가 하나의 트랜잭션으로 처리되는 구조를 표현한다.

### 5.2 도메인 중심 흐름

```mermaid
sequenceDiagram
    actor Admin as 어드민
    participant Auth as 인증/인가
    participant Brand as 브랜드
    participant Product as 상품

    Admin->>Brand: 브랜드 삭제 요청
    Brand->>Auth: 사용자 식별 및 ADMIN 권한 확인
    Auth-->>Brand: 검증 완료
    Brand->>Brand: 브랜드 존재 여부 확인

    alt 브랜드가 존재하지 않음
        Brand-->>Admin: 삭제 실패
    else 브랜드가 존재함
        Brand->>Product: 하위 상품 전체 soft delete
        Product-->>Brand: 처리 완료
        Brand->>Brand: 브랜드 soft delete
        Brand-->>Admin: 브랜드 삭제 성공
    end
```

### 5.3 내부 클래스 흐름

```mermaid
sequenceDiagram
    actor Admin as 어드민
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandAdminFacade
    participant AdminAuth as AdminAuthService
    participant BrandService as BrandService
    participant ProductService as ProductService
    participant Brand as Brand
    participant BrandRepository as BrandRepository
    participant ProductRepository as ProductRepository

    Admin->>Controller: 브랜드 삭제 요청<br/>(X-Loopers-Ldap)
    Controller->>Facade: deleteBrand(ldap, brandId)
    activate Facade

    Facade->>AdminAuth: verifyAdmin(ldap)
    activate AdminAuth
    AdminAuth->>AdminAuth: 사용자 식별 및 ADMIN role 확인
    AdminAuth-->>Facade: 검증 완료
    deactivate AdminAuth

    Facade->>BrandService: getBrand(brandId)
    activate BrandService
    BrandService->>BrandRepository: findById(brandId)
    activate BrandRepository
    BrandRepository-->>BrandService: 브랜드 조회 결과
    deactivate BrandRepository
    BrandService-->>Facade: 브랜드 조회 결과
    deactivate BrandService

    alt 브랜드가 존재하지 않음
        Facade-->>Controller: 삭제 실패 결과
        Controller-->>Admin: 삭제 실패 응답
    else 브랜드가 존재함
        rect rgb(235, 245, 255)
            Note over Facade,ProductRepository: TX - 브랜드 및 하위 상품 소프트 딜리트
            Facade->>ProductService: softDeleteAllByBrandId(brandId)
            activate ProductService
            ProductService->>ProductRepository: softDeleteAllByBrandId(brandId)
            activate ProductRepository
            ProductRepository-->>ProductService: 소프트 딜리트 완료
            deactivate ProductRepository
            ProductService-->>Facade: 상품 소프트 딜리트 완료
            deactivate ProductService

            Facade->>BrandService: softDelete(brand)
            activate BrandService
            BrandService->>Brand: softDelete()
            activate Brand
            Brand-->>BrandService: 소프트 딜리트 반영
            deactivate Brand
            BrandService->>BrandRepository: save(brand)
            activate BrandRepository
            BrandRepository-->>BrandService: 저장 완료
            deactivate BrandRepository
            BrandService-->>Facade: 브랜드 소프트 딜리트 완료
            deactivate BrandService
        end

        Facade-->>Controller: 삭제 성공 결과
        Controller-->>Admin: 삭제 성공 응답
    end
    deactivate Facade
```
