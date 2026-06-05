# Sequence Diagrams

## 설계 의도

이 문서는 `01-requirements.md`와 `04-erd.md`를 기준으로 한 목표 런타임 협력 흐름을 정리한다.
현재 구현된 user 도메인의 패턴을 참고하지만, 엔드포인트와 컬럼명은 요구사항/ERD 문서를 우선한다.

시퀀스 다이어그램은 API 호출 목록이 아니라 유스케이스의 책임 흐름을 보여주는 데 집중한다.
따라서 API별 Controller 이름은 대부분 `CustomerAPI`, `AdminAPI` boundary로 추상화하고, 관련 endpoint는 각 다이어그램 아래에 별도로 남긴다.

검증 관점은 다음과 같다.

- 사용자/관리자 인증 헤더가 API boundary에서 분리되는가?
- 도메인 간 협력은 Facade에서 조합되고, Service끼리 직접 의존하지 않는가?
- 여러 도메인 상태를 함께 바꾸는 유스케이스가 하나의 트랜잭션 경계 안에서 처리되는가?
- 좋아요 멱등성, 재고 부족 전체 거부, 브랜드 삭제 시 상품 soft delete 같은 핵심 정책이 흐름 안에 드러나는가?

## 표기 규칙

- `CustomerAPI`, `AdminAPI`는 HTTP Controller 계층을 추상화한 boundary다. (영문 유지)
- `Facade`는 유스케이스 조합 책임, `Service`는 도메인 규칙 수행 책임, `Repository`는 영속성 port 책임을 뜻한다.
- 단일 도메인 변경은 Service가 트랜잭션 경계를 가진다. 여러 도메인을 함께 변경하는 유스케이스는 Facade가 트랜잭션 경계를 가진다.
- 메시지 라벨은 메서드 시그니처가 아니라 **한글 자연어로 의도**를 표기한다.
- 도메인 예외는 `예외(제약조건 간단 설명)` 형식으로 표기한다. 예: `예외(현재 비밀번호 불일치)`, `예외(재고 부족)`.
- HTTP 상태 코드는 코드 + 한글로 표기한다. 예: `200 성공`, `201 생성됨`, `204 응답 본문 없음`, `400 잘못된 요청`, `401 인증 실패`, `404 자원 없음`, `409 충돌`.

## 1. 공통 사용자 인증 흐름

로그인 필요 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더로 사용자를 식별한다.
여정별 다이어그램에서는 이 흐름을 "고객 인증" 노트로 축약한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant Resolver as LoginUserArgumentResolver
    participant UserFacade
    participant UserService
    participant UserRepository
    participant PasswordEncoder
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 로그인 헤더와 함께 요청
    CustomerAPI->>Resolver: 로그인 사용자 식별
    alt 인증 헤더 누락
        Resolver-->>Advice: 예외(인증 헤더 누락)
        Advice-->>Customer: 401 인증 실패
    else 인증 헤더 존재
        Resolver->>UserFacade: 사용자 식별 위임
        UserFacade->>UserService: 사용자 식별 위임
        Note over UserService: @Transactional(readOnly = true)
        UserService->>UserRepository: 로그인 ID로 사용자 조회
        UserRepository-->>UserService: 사용자 정보
        UserService->>PasswordEncoder: 비밀번호 일치 확인
        alt 사용자 부재 또는 비밀번호 불일치
            UserService-->>Advice: 예외(인증 실패)
            Advice-->>Customer: 401 인증 실패
        else 인증 성공
            UserService-->>UserFacade: 사용자 정보
            UserFacade-->>Resolver: 사용자 정보
            Resolver-->>CustomerAPI: 사용자 정보
        end
    end
```

관련 API:

- 로그인 필요 API 전체

## 2. 공통 관리자 인증 흐름

관리자 API는 `/api-admin/v1` prefix와 `X-Loopers-Ldap` 헤더를 사용한다.
초기 구현은 관리자 전용 Controller 메서드에서 헤더를 직접 검증하고, 반복이 커지면 별도 resolver로 분리할 수 있다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant AdminAPI
    participant AdminService
    participant Advice as ApiControllerAdvice

    Admin->>AdminAPI: LDAP 헤더와 함께 요청
    alt LDAP 헤더 누락
        AdminAPI-->>Advice: 예외(인증 헤더 누락)
        Advice-->>Admin: 401 인증 실패
    else LDAP 헤더 존재
        AdminAPI->>AdminService: authenticate(ldap)
        AdminService-->>AdminAPI: 관리자 정보
    end
```

관련 API:

- 관리자 API 전체 (`/api-admin/v1/**`)

## 3. User-J1 첫 주문

신규 사용자가 가입 후 상품을 탐색하고, 관심 상품을 좋아요로 표시한 뒤 주문을 확정하는 정상 흐름이다.
이 다이어그램은 전체 여정의 책임 연결을 보여주고, 좋아요 멱등성과 재고 부족 실패 정책은 별도 다이어그램에서 상세히 다룬다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant UserFacade
    participant CatalogFacade as ProductFacade
    participant LikeFacade
    participant OrderFacade
    participant ProductService
    participant StockService
    participant OrderService
    participant OrderRepository
    participant PaymentService
    participant PaymentRepository
    participant PaymentGateway as PaymentGatewayPort

    Customer->>CustomerAPI: 회원가입 요청
    CustomerAPI->>UserFacade: 회원가입 처리
    UserFacade-->>CustomerAPI: 사용자 정보

    Customer->>CustomerAPI: 상품 탐색
    CustomerAPI->>CatalogFacade: 상품 목록 조회
    CatalogFacade-->>CustomerAPI: 상품 후보 목록
    CustomerAPI->>CatalogFacade: 상품 상세 조회
    CatalogFacade-->>CustomerAPI: 선택 상품

    Customer->>CustomerAPI: 관심 상품 표시
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>LikeFacade: 좋아요 등록
    LikeFacade-->>CustomerAPI: 좋아요 상태

    Customer->>CustomerAPI: 주문 요청
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>OrderFacade: 주문 처리
    Note over OrderFacade: TX1 — 주문·재고·결제 요청 기록
    OrderFacade->>ProductService: 주문 시점 상품 스냅샷 조회
    ProductService-->>OrderFacade: 상품명·단가
    OrderFacade->>StockService: 재고 차감
    StockService-->>OrderFacade: 차감 완료
    OrderFacade->>OrderService: 주문 생성 (스냅샷 포함)
    OrderService-->>OrderFacade: 주문 정보
    OrderFacade->>OrderRepository: 주문 저장
    OrderRepository-->>OrderFacade: 저장 완료
    OrderFacade->>PaymentService: 결제 요청 기록
    PaymentService->>PaymentRepository: 결제 요청 기록 (REQUESTED)
    Note over OrderFacade: TX1 commit — 외부 호출은 트랜잭션 밖
    OrderFacade->>PaymentService: 외부 결제 승인 요청
    PaymentService->>PaymentGateway: 외부 결제 승인 요청
    PaymentGateway-->>PaymentService: 승인 결과
    Note over OrderFacade: TX2 — 결제 결과 반영
    PaymentService->>PaymentRepository: 결제 승인 기록 (APPROVED)
    PaymentService-->>OrderFacade: 결제 정보
    OrderFacade-->>CustomerAPI: 주문 정보
    CustomerAPI-->>Customer: 주문 확정
```

관련 API:

- `POST /api/v1/users`
- `GET /api/v1/products`
- `GET /api/v1/products/{productId}`
- `POST /api/v1/products/{productId}/likes`
- `POST /api/v1/orders`
- `GET /api/v1/orders/{orderId}`

핵심 포인트:

- 상품 탐색은 `ProductFacade`로 묶고, 주문 생성의 핵심 책임인 스냅샷 생성, 재고 차감, 주문 저장, 결제 기록을 중심으로 표현한다.
- 주문 항목에는 상품명과 단가 스냅샷이 저장되어 이후 상품 변경과 독립적으로 과거 주문을 보여준다.
- 외부 결제 호출은 어떤 DB 트랜잭션에도 속하지 않는다. `OrderFacade` 는 (TX1 → 외부 호출 → TX2) 세 구간으로 유스케이스를 구성한다. outbox 는 결제수단과 외부 연동 방식이 구체화되면 추가한다.

## 4. User-J3 비밀번호 변경

비밀번호 변경은 현재 인증이 이미 끝났더라도, 저장 직전 최신 비밀번호를 잠금 조회 후 다시 검증한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant UserFacade
    participant UserService
    participant UserRepository
    participant PasswordEncoder
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 비밀번호 변경 요청
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>UserFacade: 비밀번호 변경 처리
    UserFacade->>UserService: 비밀번호 변경 처리
    Note over UserService: @Transactional — 잠금 조회 후 재검증
    UserService->>UserRepository: 사용자 잠금 조회
    UserRepository-->>UserService: 사용자 정보 (잠금)
    UserService->>PasswordEncoder: 현재 비밀번호 일치 확인
    alt 현재 비밀번호 불일치
        UserService-->>Advice: 예외(현재 비밀번호 불일치)
        Advice-->>Customer: 401 인증 실패
    else 새 비밀번호 정책 위반 또는 현재 비밀번호와 동일
        UserService-->>Advice: 예외(새 비밀번호 정책 위반)
        Advice-->>Customer: 400 잘못된 요청
    else 유효한 새 비밀번호
        UserService->>PasswordEncoder: 새 비밀번호 인코딩
        UserService->>UserRepository: 비밀번호 저장
        UserService-->>UserFacade: 변경 완료
        UserFacade-->>CustomerAPI: 변경 완료
        CustomerAPI-->>Customer: 200 성공
    end
```

관련 API:

- `PUT /api/v1/users/password`

핵심 포인트:

- 현재 비밀번호 불일치는 `401 인증 실패`로 응답한다.
- 새 비밀번호 포맷 오류, 생년월일 토큰 포함, 현재 비밀번호와 동일한 새 비밀번호는 모두 `400 잘못된 요청`으로 묶는다.

## 5. User-J4 좋아요 토글과 목록 조회

좋아요는 사용자와 상품 쌍의 현재 상태다.
`POST`와 `DELETE`는 최종 상태 기준으로 멱등하게 동작한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant LikeFacade
    participant ProductService
    participant LikeService
    participant LikeRepository
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 좋아요 상태 변경 요청
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>LikeFacade: 좋아요 상태 전이 (등록 또는 취소)
    LikeFacade->>ProductService: 상품 존재 확인
    alt 상품 없음
        ProductService-->>Advice: 예외(상품 없음)
        Advice-->>Customer: 404 자원 없음
    else 상품 존재
        LikeFacade->>LikeService: 좋아요 상태 적용
        Note over LikeService: @Transactional — 멱등 처리
        LikeService->>LikeRepository: 좋아요 존재 여부 확인
        alt 등록 요청이고 이미 좋아요 상태
            LikeService-->>LikeFacade: 좋아요 유지 (멱등)
        else 등록 요청이고 미좋아요 상태
            LikeService->>LikeRepository: 좋아요 저장
            LikeRepository-->>LikeService: 저장 완료
        else 취소 요청
            LikeService->>LikeRepository: 좋아요 삭제
            LikeRepository-->>LikeService: 삭제 완료 또는 부재 (멱등)
        end
        LikeFacade-->>CustomerAPI: 최종 좋아요 상태
        CustomerAPI-->>Customer: 200 성공 또는 204 응답 본문 없음
    end

    Customer->>CustomerAPI: 내 좋아요 목록 조회
    Note over CustomerAPI: 고객 인증 후 path userId 와 인증 사용자 비교
    alt path userId 와 인증 사용자 불일치
        CustomerAPI-->>Advice: 예외(타인 자원 접근)
        Advice-->>Customer: 404 자원 없음
    else 본인 자원
        CustomerAPI->>LikeFacade: 내 좋아요 조회
        LikeFacade->>LikeService: 사용자별 좋아요 조회
        LikeService->>LikeRepository: 사용자 ID 로 좋아요 조회
        LikeRepository-->>LikeService: 좋아요 목록
        LikeService-->>LikeFacade: 좋아요 목록
        LikeFacade-->>CustomerAPI: 좋아요 목록
        CustomerAPI-->>Customer: 200 성공
    end
```

관련 API:

- `POST /api/v1/products/{productId}/likes`
- `DELETE /api/v1/products/{productId}/likes`
- `GET /api/v1/users/{userId}/likes`

핵심 포인트:

- 좋아요 목록은 user에서 likes 컬렉션을 양방향으로 열지 않고 `LikeRepository.findByUserId` 명시 쿼리로 조회한다.
- 좋아요 이력이 아니라 현재 상태만 필요하므로 취소는 hard delete를 기본으로 둔다.
- 본인 외 자원 접근은 자원 존재 여부를 노출하지 않기 위해 `404 자원 없음`으로 응답한다.

## 6. User-E2 재고 부족 거부

주문 항목 중 하나라도 재고가 부족하면 주문 전체를 거부하고 어떤 항목도 차감하지 않는다.
이 다이어그램은 부분 성공 금지 정책을 검증하므로 트랜잭션과 잠금 조회를 상세히 표현한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant OrderFacade
    participant ProductService
    participant StockService
    participant StockRepository
    participant OrderService
    participant OrderRepository
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 주문 요청
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>OrderFacade: 주문 처리
    Note over OrderFacade: @Transactional — 부분 성공 금지
    OrderFacade->>ProductService: 주문 시점 상품 스냅샷 조회
    ProductService-->>OrderFacade: 상품명·단가
    OrderFacade->>StockService: 재고 차감
    StockService->>StockRepository: 재고 행 잠금 조회
    StockRepository-->>StockService: 잠금된 재고 행
    alt 어떤 항목이라도 재고 부족
        StockService-->>OrderFacade: 예외(재고 부족)
        Note over OrderFacade: 트랜잭션 전체 롤백 — 어떤 항목도 차감되지 않음
        OrderFacade-->>Advice: 예외(재고 부족)
        Advice-->>Customer: 409 충돌 (부족 항목 안내)
    else 모든 항목 가용
        StockService->>StockRepository: 요청 수량만큼 차감
        StockService-->>OrderFacade: 차감 완료
        OrderFacade->>OrderService: 주문 생성 (스냅샷 포함)
        OrderService-->>OrderFacade: 주문 정보
        OrderFacade->>OrderRepository: 주문 저장
        OrderRepository-->>OrderFacade: 저장 완료
        OrderFacade-->>CustomerAPI: 주문 정보
        CustomerAPI-->>Customer: 201 생성됨
    end
```

관련 API:

- `POST /api/v1/orders`

핵심 포인트:

- `StockRepository.findStocksForUpdate`로 주문 대상 재고 행을 잠금 조회한다.
- 재고 부족 시 `OrderRepository.save`에 도달하지 않고 전체 트랜잭션이 rollback된다.
- 재고 변경 근거는 주문 항목으로 추적한다. 입고/수동 보정이 생기면 `stock_movements`가 필요하다.

## 7. User-E3 결제 승인 실패

외부 결제 시스템이 승인을 거절하거나 장애를 반환하면 결제 실패 기록을 남긴다.
현재 설계는 결제 기록 테이블을 먼저 확정하고, outbox 는 결제수단과 연동 방식이 구체화되면 추가한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant OrderFacade
    participant ProductService
    participant StockService
    participant OrderService
    participant OrderRepository
    participant PaymentService
    participant PaymentRepository
    participant PaymentGateway as PaymentGatewayPort
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 주문 요청
    Note over CustomerAPI: 고객 인증 (§1 참조)
    CustomerAPI->>OrderFacade: 주문 처리
    Note over OrderFacade: TX1 — 주문·재고·결제 요청 기록
    OrderFacade->>ProductService: 주문 시점 상품 스냅샷 조회
    ProductService-->>OrderFacade: 상품명·단가
    OrderFacade->>StockService: 재고 차감
    StockService-->>OrderFacade: 차감 완료
    OrderFacade->>OrderService: 주문 생성 (스냅샷 포함)
    OrderService-->>OrderFacade: 주문 정보
    OrderFacade->>OrderRepository: 주문 저장
    OrderRepository-->>OrderFacade: 저장 완료
    OrderFacade->>PaymentService: 결제 요청 기록
    PaymentService->>PaymentRepository: 결제 요청 기록 (REQUESTED)
    Note over OrderFacade: TX1 commit — 외부 호출은 트랜잭션 밖
    OrderFacade->>PaymentService: 외부 결제 승인 요청
    PaymentService->>PaymentGateway: 외부 결제 승인 요청
    alt 결제 승인 거절
        PaymentGateway-->>PaymentService: 거절 결과
        Note over OrderFacade: TX2 — 결제 실패 기록 + 재고 보상 복구
        PaymentService->>PaymentRepository: 결제 실패 기록 (FAILED)
        OrderFacade->>StockService: 차감 재고 보상 복구
        PaymentService-->>OrderFacade: 예외(결제 승인 실패)
        OrderFacade-->>Advice: 예외(결제 승인 실패)
        Advice-->>Customer: 409 충돌
    else 외부 결제 시스템 장애
        PaymentGateway-->>PaymentService: 타임아웃 또는 장애
        Note over OrderFacade: TX2 — 결제 실패 기록 + 재고 보상 복구
        PaymentService->>PaymentRepository: 결제 실패 기록 (FAILED)
        OrderFacade->>StockService: 차감 재고 보상 복구
        PaymentService-->>OrderFacade: 예외(외부 결제 시스템 실패)
        OrderFacade-->>Advice: 예외(외부 결제 시스템 실패)
        Advice-->>Customer: 502 외부 시스템 실패
    end
```

관련 API:

- `POST /api/v1/orders`

핵심 포인트:

- 결제 실패도 `payments` 에 기록해 주문 시도와 외부 승인 결과를 추적한다.
- 재고 복구는 트랜잭션 롤백이 아니라 **보상 트랜잭션(TX2)** 이다. TX1 이 이미 커밋되어 자동 원복할 수 없기 때문이다.
- TX1 commit 후 TX2 도달 전에 프로세스가 종료되면 `payments.status = REQUESTED` 잔여 상태가 남는다. 회수 전략(상태 폴링·웹훅·outbox)은 결제수단과 외부 연동 방식이 구체화될 때 확정한다.

## 8. Admin-J1 신규 브랜드와 상품 등록

관리자가 브랜드를 등록하고, 등록된 브랜드에 상품과 초기 재고를 연결해 노출시키는 흐름이다.
변경 작업은 `admin_operation_logs`에 기록한다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant AdminAPI
    participant BrandFacade
    participant ProductFacade
    participant BrandService
    participant ProductService
    participant StockService
    participant LogService as AdminOperationLogService
    participant Advice as ApiControllerAdvice

    Admin->>AdminAPI: 브랜드 등록 요청
    Note over AdminAPI: 관리자 인증 (§2 참조)
    AdminAPI->>BrandFacade: 브랜드 등록 처리
    Note over BrandFacade: @Transactional — 등록 + 변경 기록 단일 경계
    BrandFacade->>BrandService: 브랜드 등록
    BrandService-->>BrandFacade: 브랜드 정보
    BrandFacade->>LogService: 변경 기록 적재 (BRAND, brandId, CREATED)
    BrandFacade-->>AdminAPI: 브랜드 정보
    AdminAPI-->>Admin: 201 생성됨

    Admin->>AdminAPI: 상품 등록 요청
    Note over AdminAPI: 관리자 인증 (§2 참조)
    AdminAPI->>ProductFacade: 상품 등록 처리
    Note over ProductFacade: @Transactional — 등록 + 재고 초기화 + 변경 기록 단일 경계
    ProductFacade->>BrandService: 브랜드 존재 확인
    alt 미등록 브랜드
        BrandService-->>Advice: 예외(미등록 브랜드)
        Advice-->>Admin: 409 충돌
    else 브랜드 존재
        BrandService-->>ProductFacade: 브랜드 정보
        ProductFacade->>ProductService: 상품 등록
        ProductService-->>ProductFacade: 상품 정보
        ProductFacade->>StockService: 재고 초기화
        StockService-->>ProductFacade: 재고 정보
        ProductFacade->>LogService: 변경 기록 적재 (PRODUCT, productId, CREATED)
        ProductFacade-->>AdminAPI: 상품 정보
        AdminAPI-->>Admin: 201 생성됨
    end
```

관련 API:

- `POST /api-admin/v1/brands`
- `POST /api-admin/v1/products`

핵심 포인트:

- 상품 등록은 이미 등록된 브랜드에만 허용한다. 시스템 상태와의 충돌이므로 `400 잘못된 요청`이 아니라 `409 충돌`로 응답한다.
- 초기 재고는 상품 생명주기에 종속되며, 별도 삭제 시각을 갖지 않는다.
- 관리자 작업 로그는 변경 대상 검증과 저장이 성공한 뒤 기록한다. 조회 API와 실패한 변경 요청은 성공 이력으로 기록하지 않는다.

## 9. Admin-J2 브랜드 삭제와 상품 soft delete

브랜드 삭제 시 소속 상품도 함께 삭제 상태로 전환한다.
재고는 상품에 종속되므로 별도 삭제 호출을 두지 않는다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant AdminAPI
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant LogService as AdminOperationLogService

    Admin->>AdminAPI: 브랜드 삭제 요청
    Note over AdminAPI: 관리자 인증 (§2 참조)
    AdminAPI->>BrandFacade: 브랜드 삭제 처리
    Note over BrandFacade: @Transactional — 브랜드·소속 상품 soft delete + 변경 기록 단일 경계
    BrandFacade->>BrandService: 브랜드 soft delete
    BrandService-->>BrandFacade: 브랜드 정보
    BrandFacade->>ProductService: 소속 상품 일괄 soft delete
    ProductService-->>BrandFacade: 삭제된 상품 ID 목록
    BrandFacade->>LogService: 변경 기록 적재 (BRAND, brandId, DELETED)
    loop 각 삭제된 상품
        BrandFacade->>LogService: 변경 기록 적재 (PRODUCT, productId, DELETED)
    end
    BrandFacade-->>AdminAPI: 처리 완료
    AdminAPI-->>Admin: 204 응답 본문 없음
```

관련 API:

- `DELETE /api-admin/v1/brands/{brandId}`

핵심 포인트:

- DB cascade 대신 애플리케이션 유스케이스에서 브랜드와 상품을 함께 soft delete한다.
- 과거 주문 내역은 `OrderItem` 스냅샷으로 보존되므로 상품 soft delete 이후에도 훼손되지 않는다.
- 관리자 로그의 `target_id`는 브랜드/상품 다형 대상이므로 DB FK를 강제하지 않는다.
- 관리자 작업 로그는 브랜드·소속 상품 soft delete 성공 후 같은 유스케이스 경계 안에서 기록한다.

## 10. 상품/브랜드 공개 조회

익명 방문자와 일반 사용자는 인증 없이 브랜드 단건, 상품 목록, 상품 상세를 조회할 수 있다.
조회 흐름은 쓰기 트랜잭션이 아니라 필터와 정렬, 삭제 상태 제외 조건을 확인하는 데 집중한다.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CustomerAPI
    participant ProductFacade
    participant BrandService
    participant ProductService
    participant ProductRepository
    participant Advice as ApiControllerAdvice

    Customer->>CustomerAPI: 브랜드 단건 조회
    CustomerAPI->>ProductFacade: 브랜드 조회 위임
    ProductFacade->>BrandService: 브랜드 조회
    alt 브랜드 없음 또는 삭제됨
        BrandService-->>Advice: 예외(브랜드 없음)
        Advice-->>Customer: 404 자원 없음
    else 노출 가능한 브랜드
        BrandService-->>ProductFacade: 브랜드 정보
        ProductFacade-->>CustomerAPI: 브랜드 정보
        CustomerAPI-->>Customer: 200 성공
    end

    Customer->>CustomerAPI: 상품 목록 조회 (brandId, sort, page, size)
    CustomerAPI->>ProductFacade: 상품 목록 조회
    ProductFacade->>ProductService: 상품 목록 조회 조건 검증
    ProductService->>ProductRepository: 삭제되지 않은 상품 목록 조회
    ProductRepository-->>ProductService: 상품 페이지
    ProductService-->>ProductFacade: 상품 페이지
    ProductFacade-->>CustomerAPI: 상품 페이지
    CustomerAPI-->>Customer: 200 성공

    Customer->>CustomerAPI: 상품 상세 조회
    CustomerAPI->>ProductFacade: 상품 상세 조회
    ProductFacade->>ProductService: 상품 조회
    alt 상품 없음 또는 삭제됨
        ProductService-->>Advice: 예외(상품 없음)
        Advice-->>Customer: 404 자원 없음
    else 노출 가능한 상품
        ProductService-->>ProductFacade: 상품 정보
        ProductFacade-->>CustomerAPI: 상품 정보
        CustomerAPI-->>Customer: 200 성공
    end
```

관련 API:

- `GET /api/v1/brands/{brandId}`
- `GET /api/v1/products`
- `GET /api/v1/products/{productId}`

핵심 포인트:

- 공개 조회는 인증이 필요 없지만, soft delete 된 브랜드/상품은 노출하지 않는다.
- 최신순(`latest`) 정렬은 필수이고, 가격 낮은 순·좋아요 많은 순은 선택 확장이다.

## 11. Admin-J3/J4 카탈로그 변경과 주문 모니터링

관리자는 브랜드/상품을 수정하거나 상품을 단건 삭제할 수 있고, 주문 운영 현황을 조회할 수 있다.
카탈로그 변경 작업은 관리자 변경 로그 대상이며, 주문 조회는 읽기 작업이므로 로그 대상이 아니다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant AdminAPI
    participant BrandFacade
    participant ProductFacade
    participant OrderFacade
    participant BrandService
    participant ProductService
    participant OrderService
    participant LogService as AdminOperationLogService
    participant Advice as ApiControllerAdvice

    Admin->>AdminAPI: 브랜드 또는 상품 수정 요청
    Note over AdminAPI: 관리자 인증 (§2 참조)
    alt 브랜드 수정
        AdminAPI->>BrandFacade: 브랜드 수정 처리
        Note over BrandFacade: @Transactional — 수정 + 변경 기록
        BrandFacade->>BrandService: 브랜드 이름 변경
        BrandService-->>BrandFacade: 브랜드 정보
        BrandFacade->>LogService: 변경 기록 적재 (BRAND, brandId, UPDATED)
        BrandFacade-->>AdminAPI: 브랜드 정보
        AdminAPI-->>Admin: 200 성공
    else 상품 수정
        AdminAPI->>ProductFacade: 상품 수정 처리
        Note over ProductFacade: @Transactional — 수정 + 변경 기록
        ProductFacade->>ProductService: 상품 정보 변경
        alt 브랜드 변경 시도
            ProductService-->>Advice: 예외(브랜드 변경 불가)
            Advice-->>Admin: 409 충돌
        else 변경 가능
            ProductService-->>ProductFacade: 상품 정보
            ProductFacade->>LogService: 변경 기록 적재 (PRODUCT, productId, UPDATED)
            ProductFacade-->>AdminAPI: 상품 정보
            AdminAPI-->>Admin: 200 성공
        end
    end

    Admin->>AdminAPI: 상품 단건 삭제 요청
    Note over AdminAPI: 관리자 인증 (§2 참조)
    AdminAPI->>ProductFacade: 상품 삭제 처리
    Note over ProductFacade: @Transactional — 상품 soft delete + 변경 기록
    ProductFacade->>ProductService: 상품 soft delete
    ProductService-->>ProductFacade: 상품 정보
    ProductFacade->>LogService: 변경 기록 적재 (PRODUCT, productId, DELETED)
    ProductFacade-->>AdminAPI: 처리 완료
    AdminAPI-->>Admin: 204 응답 본문 없음

    Admin->>AdminAPI: 주문 목록 또는 상세 조회
    Note over AdminAPI: 관리자 인증 (§2 참조)
    AdminAPI->>OrderFacade: 관리자 주문 조회
    OrderFacade->>OrderService: 주문 조회
    OrderService-->>OrderFacade: 주문 정보
    OrderFacade-->>AdminAPI: 주문 정보
    AdminAPI-->>Admin: 200 성공
```

관련 API:

- `PUT /api-admin/v1/brands/{brandId}`
- `PUT /api-admin/v1/products/{productId}`
- `DELETE /api-admin/v1/products/{productId}`
- `GET /api-admin/v1/orders`
- `GET /api-admin/v1/orders/{orderId}`

핵심 포인트:

- 상품 브랜드 변경은 등록 후 불변 정책이므로 `409 충돌`로 거부한다.
- 상품 단건 삭제는 브랜드 삭제와 달리 해당 상품만 soft delete 한다.
- 관리자 작업 로그는 변경 저장 성공 후 기록한다. 브랜드 변경 시도처럼 실패한 요청은 성공 이력으로 기록하지 않는다.
- 관리자 주문 조회는 읽기 작업이므로 `admin_operation_logs` 기록 대상이 아니다.
