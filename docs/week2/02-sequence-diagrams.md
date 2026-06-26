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
    participant Coupon as 쿠폰
    participant Product as 상품/재고
    participant Payment as 외부 결제 시스템

    User->>Order: 주문 요청<br/>(상품 ID, 수량 목록, 발급 쿠폰 ID)
    Order->>Auth: 사용자 식별
    Auth-->>Order: 사용자 정보
    Order->>Product: 상품 존재 여부와 재고 확인
    opt 발급 쿠폰 사용
        Order->>Coupon: 발급 쿠폰 소유/사용 가능 여부 확인
        Coupon-->>Order: 쿠폰 사용 가능
    end

    alt 주문 가능
        Product-->>Order: 주문 가능
        Order->>Product: 재고 차감
        Product-->>Order: 재고 차감 완료
        opt 발급 쿠폰 사용
            Order->>Coupon: 쿠폰 사용 처리
            Coupon-->>Order: 쿠폰 사용 완료
        end
        Order->>Order: 주문 생성<br/>(PENDING_PAYMENT, 상품 스냅샷, 금액 스냅샷)
        Order->>Payment: 결제 승인 요청
        Payment-->>Order: 결제 승인 결과

        alt 결제 성공
            alt 주문 확정 성공 또는 이미 PAID
                Order->>Order: 주문 상태를 PAID로 확정
                Order-->>User: 주문 정보
            else 주문이 이미 실패/취소됨
                Order->>Payment: 결제 취소 요청
                Payment-->>Order: 결제 취소 접수
                Order-->>User: 주문 확정 실패 응답
            end
        else 결제 실패
            Order->>Order: 주문 실패 처리<br/>(PAYMENT_FAILED)
            Order->>Product: 재고 차감 취소
            Product-->>Order: 재고 차감 취소 완료
            opt 발급 쿠폰 사용 주문
                Order->>Coupon: 쿠폰 사용 취소
                Coupon-->>Order: 쿠폰 사용 취소 완료
            end
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
    participant PrepareService as OrderPrepareService
    participant ConfirmService as OrderConfirmService
    participant ReleaseService as OrderReleaseService
    participant UserService as UserApplicationService
    participant ProductService as ProductApplicationService
    participant StockService as StockApplicationService
    participant CouponService as CouponApplicationService
    participant OrderService as OrderApplicationService
    participant PaymentGateway as PaymentGateway

    User->>Controller: 주문 요청<br/>(상품 ID, 수량 목록, 발급 쿠폰 ID, 로그인 헤더)
    Controller->>Facade: placeOrder(command)
    activate Facade

    rect rgb(235, 245, 255)
        Note over PrepareService,OrderService: TX1 - 주문 준비
        Facade->>PrepareService: prepare(command)
        activate PrepareService
        PrepareService->>UserService: getUser(userId)
        UserService-->>PrepareService: 사용자
        PrepareService->>ProductService: getProduct(productId)
        ProductService-->>PrepareService: 상품
        PrepareService->>StockService: deduct(productId, quantity)
        StockService-->>PrepareService: 재고 차감 완료
        opt 발급 쿠폰 사용
            PrepareService->>CouponService: getUsableCoupon(userId, userCouponId)
            CouponService-->>PrepareService: 쿠폰 정책
        end
        PrepareService->>PrepareService: OrderAmountCalculator.calculate(items, coupon)
        opt 발급 쿠폰 사용
            PrepareService->>CouponService: useCoupon(userId, userCouponId)
            CouponService-->>PrepareService: 쿠폰 사용 완료
        end
        PrepareService->>OrderService: createOrder(userId, userCouponId, items, amounts)
        OrderService-->>PrepareService: PENDING_PAYMENT 주문
        PrepareService-->>Facade: 준비된 주문
        deactivate PrepareService
    end

    rect rgb(245, 245, 245)
        Note over Facade,PaymentGateway: 외부 결제 호출 - DB 트랜잭션 밖
        Facade->>PaymentGateway: pay(orderId, userId, paymentAmount)
        activate PaymentGateway
        PaymentGateway-->>Facade: 결제 결과
        deactivate PaymentGateway
    end

    alt 결제 성공
        rect rgb(235, 255, 235)
            Note over ConfirmService,OrderService: TX2 - 주문 확정
            Facade->>ConfirmService: confirm(orderId)
            ConfirmService->>OrderService: markPaid(orderId)
            OrderService-->>ConfirmService: Confirmed / AlreadyPaid / AlreadyTerminated
            ConfirmService-->>Facade: 확정 결과
        end
        alt Confirmed 또는 AlreadyPaid
            Facade-->>Controller: 주문 정보
            Controller-->>User: 주문 정보
        else AlreadyTerminated 또는 확정 실패
            Facade->>PaymentGateway: cancel(orderId, userId, amount)
            PaymentGateway-->>Facade: 결제 취소 접수
            Facade-->>Controller: 주문 확정 실패
            Controller-->>User: 주문 확정 실패 응답
        end
    else 결제 실패 또는 외부 연동 실패
        rect rgb(255, 240, 240)
            Note over ReleaseService,CouponService: TX3 - 주문 실패 및 보상
            Facade->>ReleaseService: markPaymentFailed(orderId)
            ReleaseService->>OrderService: markPaymentFailed(orderId)
            OrderService-->>ReleaseService: PAYMENT_FAILED 주문
            ReleaseService->>StockService: restore(orderItems)
            StockService-->>ReleaseService: 재고 복구 완료
            opt 쿠폰 사용 주문
                ReleaseService->>CouponService: cancelCouponUse(userId, userCouponId)
                CouponService-->>ReleaseService: 쿠폰 사용 취소 완료
            end
            ReleaseService-->>Facade: 실패 처리 주문
        end
        Facade-->>Controller: 결제 실패 결과
        Controller-->>User: 결제 실패 응답
    end
    deactivate Facade
```

## 4. 상품 좋아요 등록/취소 흐름

### 4.1 개요

상품 좋아요 등록/취소 흐름은 같은 요청이 반복되어도 결과가 안정적으로 유지되는 멱등성 정책을 중심으로 작성했다. 도메인 중심 흐름은 등록/취소 요청의 큰 흐름만 표현하고, 기존 좋아요 상태에 따른 생성, 복구, 취소 처리, no-op 분기는 내부 클래스 흐름에서 표현한다.

> **변경 사항**: 좋아요 등록/취소 시 `Product.likeCount`를 즉시 변경하지 않는다. 좋아요 수는 `LikeCountSyncJob` 배치가 `product_like_counts` 프로젝션 테이블에 주기적으로 동기화한다.

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
    participant LikeService as LikeApplicationService
    participant Like as Like
    participant LikeRepository as LikeRepository

    User->>Controller: POST /api/v1/products/{productId}/likes<br/>(로그인 헤더)
    Controller->>Facade: addLike(userId, productId)
    activate Facade

    Facade->>UserService: getUser(userId)
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
        Facade->>LikeService: activate(userId, productId)
        activate LikeService
        LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
        activate LikeRepository
        LikeRepository-->>LikeService: 좋아요 조회 결과
        deactivate LikeRepository

        alt 좋아요 없음
            rect rgb(235, 245, 255)
                Note over LikeService,LikeRepository: TX - 좋아요 생성
                LikeService->>LikeRepository: save(new Like)
                activate LikeRepository
                LikeRepository-->>LikeService: 저장 완료
                deactivate LikeRepository
            end
        else 취소된 좋아요 있음
            rect rgb(235, 245, 255)
                Note over LikeService,LikeRepository: TX - 좋아요 복구
                LikeService->>Like: restore()
                activate Like
                Like-->>LikeService: 복구 반영
                deactivate Like
            end
        else 활성 좋아요 있음
            LikeService->>LikeService: no-op
        end

        Note right of LikeService: 좋아요 수는 배치(LikeCountSyncJob)가<br/>product_like_counts에 주기적 동기화

        LikeService-->>Facade: 처리 결과 (changed: Boolean)
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
    participant LikeService as LikeApplicationService
    participant Like as Like
    participant LikeRepository as LikeRepository

    User->>Controller: DELETE /api/v1/products/{productId}/likes<br/>(로그인 헤더)
    Controller->>Facade: cancelLike(userId, productId)
    activate Facade

    Facade->>UserService: getUser(userId)
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
        Facade->>LikeService: cancel(userId, productId)
        activate LikeService
        LikeService->>LikeRepository: findByUserIdAndProductId(userId, productId)
        activate LikeRepository
        LikeRepository-->>LikeService: 좋아요 조회 결과
        deactivate LikeRepository

        alt 활성 좋아요 있음
            rect rgb(235, 245, 255)
                Note over LikeService,LikeRepository: TX - 좋아요 취소
                LikeService->>Like: cancel()
                activate Like
                Like-->>LikeService: 취소 반영
                deactivate Like
            end
        else 좋아요 없음 또는 이미 취소됨
            LikeService->>LikeService: no-op
        end

        Note right of LikeService: 좋아요 수는 배치(LikeCountSyncJob)가<br/>product_like_counts에 주기적 동기화

        LikeService-->>Facade: 처리 결과 (changed: Boolean)
        deactivate LikeService
        Facade-->>Controller: 처리 결과
        Controller-->>User: 200 OK
    end
    deactivate Facade
```

## 5. 브랜드 삭제 및 상품 연쇄 삭제 

### 5.1 개요

브랜드 삭제는 브랜드 자체 삭제뿐 아니라 하위 상품 전체를 함께 삭제하는 연쇄 정책을 포함한다.

도메인 중심 흐름은 사용자 식별과 어드민 권한 확인, 브랜드 존재 확인, 하위 상품 삭제, 브랜드 삭제의 큰 순서를 표현한다.
내부 클래스 흐름은 하위 상품별로 상품, 재고, 좋아요 수 집계를 개별 삭제하고 캐시를 무효화한 뒤, 브랜드를 소프트 딜리트하는 구조를 표현한다.

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
        Brand->>Product: 하위 상품별 삭제 (상품 + 재고 + 좋아요 집계 + 캐시 무효화)
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
    participant BrandFacade as BrandFacade
    participant ProductFacade as ProductFacade
    participant ProductService as ProductApplicationService
    participant StockService as StockApplicationService
    participant LikeCountRepo as ProductLikeCountCommandRepository
    participant CacheService as ProductCacheService
    participant BrandService as BrandApplicationService

    Admin->>Controller: 브랜드 삭제 요청<br/>(X-Loopers-Ldap)
    Controller->>BrandFacade: deleteBrand(brandId)
    activate BrandFacade

    rect rgb(235, 245, 255)
        Note over BrandFacade,BrandService: TX - 브랜드 및 하위 상품 연쇄 삭제
        BrandFacade->>ProductService: findActiveIdsByBrandId(brandId)
        ProductService-->>BrandFacade: 상품 ID 목록

        loop 각 상품 ID에 대해
            BrandFacade->>ProductFacade: deleteProduct(productId)
            activate ProductFacade
            ProductFacade->>ProductService: deleteProduct(productId)
            ProductFacade->>StockService: deleteStock(productId)
            ProductFacade->>LikeCountRepo: deleteByProductId(productId)
            ProductFacade->>CacheService: evictProductDetail(productId)
            deactivate ProductFacade
        end

        BrandFacade->>BrandService: deleteBrand(brandId)
        BrandService-->>BrandFacade: 브랜드 소프트 딜리트 완료
    end

    BrandFacade-->>Controller: 삭제 성공 결과
    Controller-->>Admin: 삭제 성공 응답
    deactivate BrandFacade
```
