# 쿠폰 도메인 + 주문 트랜잭션/동시성 제어 설계

- 날짜: 2026-06-12
- 브랜치: volume-4
- 과제: 주문 시 재고/주문/쿠폰의 정합성을 트랜잭션으로 보장하고, 동시성 이슈(Lost Update)를 락으로 제어한다.

## 목표

1. 쿠폰 도메인 신규 구현 (템플릿 + 발급 쿠폰, 대고객/어드민 API)
2. 주문 API에 쿠폰 적용 + 단일 트랜잭션 정합성 보장
3. 자원별 동시성 제어: 재고=비관적 락, 쿠폰=낙관적 락, 좋아요 카운트=원자적 UPDATE
4. 기존 이슈 수정: 좋아요 카운트 유실/lost update, syncStock 비정규화 제거
5. Testcontainers MySQL 기반 동시성 테스트

## 비목표 (이번 범위 아님)

- 쿠폰 발급 수량 제한(선착순) — 발급은 1인 1매 제약만
- 좋아요/취소 더블클릭 시 500 응답의 멱등 처리
- 인증 로직의 트랜잭션 분리 (인터셉터 이동)
- 발급 쿠폰의 할인 조건 스냅샷 (템플릿 참조 구조 유지)

## 락 전략 결정

| 자원 | 전략 | 근거 |
|---|---|---|
| 재고 차감 | 비관적 락 `@Lock(PESSIMISTIC_WRITE)` | 경합이 잦고 충돌 시 재시도 비용이 큼. 락 획득 전 productId 오름차순 정렬로 데드락 방지 |
| 쿠폰 사용 | 낙관적 락 `@Version` | 같은 쿠폰 동시 사용은 비정상 시도. 한 건만 성공하면 되고, 충돌한 쪽은 재시도 없이 주문 실패(CONFLICT) |
| 좋아요 카운트 | 원자적 UPDATE 쿼리 | 값을 읽고 판단할 필요 없는 단순 증감. 행 락 직렬화 비용 회피 |

## 1. 쿠폰 도메인 (`domain/coupon`)

### CouponModel (템플릿, `coupons` 테이블)

- 필드: `name`, `type: CouponType(FIXED|RATE)`, `value: BigDecimal`, `minOrderAmount: BigDecimal?`, `expiredAt: ZonedDateTime`
- 생성 검증: RATE는 1~100, FIXED는 양수. `CoreException(BAD_REQUEST)`
- 행위:
  - `calculateDiscount(orderAmount): BigDecimal` — 최소 주문 금액 미달 시 `BAD_REQUEST`. FIXED는 `min(value, orderAmount)`, RATE는 `orderAmount × value / 100`
  - `isExpired(now): Boolean`
  - `update(...)`, soft delete (BaseEntity의 delete 패턴 따름)

### UserCouponModel (발급 쿠폰, `user_coupons` 테이블)

- 필드: `userId`, `couponId`, `status: UserCouponStatus(AVAILABLE|USED)`, `usedAt: ZonedDateTime?`, `@Version version: Long`
- 유니크 제약: `(user_id, coupon_id)` — 1인 1매
- 행위: `use(coupon: CouponModel, now)` — AVAILABLE 아니면 `CONFLICT("이미 사용된 쿠폰")`, `coupon.isExpired(now)`면 `BAD_REQUEST` → USED 전이 + usedAt 기록
- **EXPIRED는 저장 상태가 아니라 조회 시 파생** (템플릿 expiredAt 기준). 만료 배치 없음. 단, `use()`는 만료를 검증하므로 만료 쿠폰 사용은 불가능

### Repository Port (domain) / 구현 (infrastructure)

- `CouponRepository`: `save`, `findActiveById`, `findAllActive(pageable)`, soft delete
- `UserCouponRepository`: `save`, `findByIdAndUserId`, `findAllByUserId(pageable)`, `findAllByCouponId(pageable)`

## 2. 주문 변경

### CreateOrderUsecase (단일 `@Transactional`)

```text
1. 사용자 인증 (기존 userService.getProfile)
2. couponId != null 이면:
   - userCouponRepository.findByIdAndUserId(couponId, userId) → 없으면 NOT_FOUND
     (타 유저 소유 쿠폰도 NOT_FOUND — 존재 여부 노출 방지)
   - couponRepository.findActiveById(userCoupon.couponId) → 없으면 NOT_FOUND
3. command.items를 productId 오름차순 정렬
4. 정렬 순서대로 재고 비관적 락 조회:
   ProductStockRepository.findByProductIdForUpdate(productId)
5. OrderDomainService.create(userId, items, coupon?, userCoupon?):
   - 재고 차감 (기존 deduct, syncStock 호출 제거)
   - totalPrice 계산 → coupon.calculateDiscount(totalPrice) → discountAmount, paidPrice
   - userCoupon.use(coupon, now) — 도메인 규칙으로 USED 전이
   - OrderModel 생성 (금액 3종 + userCouponId 스냅샷)
6. orderRepository.save
7. 커밋 시 user_coupons @Version 충돌 → ObjectOptimisticLockingFailureException
   → CoreException(CONFLICT, "이미 사용된 쿠폰입니다.") 변환 → 전체 롤백
```

- 낙관적 락 예외 변환 위치: usecase에서 try-catch 또는 공통 예외 핸들러. usecase에서 잡아 의미 있는 메시지로 변환하는 것을 기본으로 한다.

### OrderModel 스냅샷 필드

- `totalPrice` (쿠폰 적용 전 금액, 기존 필드 유지)
- `discountAmount` (할인 금액, 기본 0)
- `paidPrice` (최종 결제 금액 = totalPrice - discountAmount)
- `userCouponId: Long?` (적용 쿠폰 추적)

### API 변경

- 주문 요청: `couponId` nullable 필드 추가
- 주문 응답: 금액 3종 포함

## 3. 기존 이슈 수정

### 좋아요 카운트 (AFTER_COMMIT 변경 유실 + lost update)

- `ProductRepository` Port에 추가: `incrementLikeCount(productId)`, `decrementLikeCount(productId)`
- 구현: `@Modifying` 원자적 UPDATE — `SET like_count = like_count + 1`, 감소는 `WHERE like_count > 0` 조건 포함
- `ProductLikeCountEventHandler`: `@Transactional(propagation = REQUIRES_NEW)` + 위 Port 메서드 호출로 변경 (dirty checking 의존 제거)
- `ProductModel.incrementLikeCount()/decrementLikeCount()` 행위 메서드는 사용처가 사라지면 제거

### syncStock 비정규화 제거

- `ProductModel.stockQuantity` 필드, `syncStock()` 제거
- `OrderDomainService`의 `product.syncStock(...)` 호출 제거 → 주문 트랜잭션에서 `products` 행 쓰기 제거 (락 경합 표면 축소)
- 사용처는 상품 상세와 목록(`ProductInfo` 공유): 상세는 `findByProductId` 단건 조회, 목록은 `findAllByProductIdIn` 배치 조회로 `ProductInfo.stockQuantity`에 채움 (API 응답 형태 불변)

## 4. API 목록

### 대고객

| METHOD | URI | Usecase | 비고 |
|---|---|---|---|
| POST | `/api/v1/coupons/{couponId}/issue` | `IssueCouponUsecase` | couponId는 템플릿 ID. 만료/삭제 템플릿이면 실패. 중복 발급은 유니크 제약 위반 → `CONFLICT` |
| GET | `/api/v1/users/me/coupons` | `GetMyCouponsUsecase` | AVAILABLE/USED/EXPIRED 상태 포함 (EXPIRED는 파생) |

인증: 기존 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 패턴 따름.

### 어드민 (`/api-admin/v1`)

| METHOD | URI | Usecase |
|---|---|---|
| GET | `/api-admin/v1/coupons?page&size` | `GetCouponsUsecase` |
| GET | `/api-admin/v1/coupons/{couponId}` | `GetCouponUsecase` |
| POST | `/api-admin/v1/coupons` | `CreateCouponUsecase` |
| PUT | `/api-admin/v1/coupons/{couponId}` | `UpdateCouponUsecase` |
| DELETE | `/api-admin/v1/coupons/{couponId}` | `DeleteCouponUsecase` (soft delete) |
| GET | `/api-admin/v1/coupons/{couponId}/issues?page&size` | `GetCouponIssuesUsecase` |

- 어드민 인증: `X-Loopers-Ldap` 헤더 존재 검증만 하는 최소 구현 (없으면 401). 별도 어드민 인증 인프라는 비목표
- 완료 후 `.http` 파일에 실행 예시 추가

## 5. 테스트 전략

### 도메인 단위 테스트

- CouponModel: 생성 검증, calculateDiscount (FIXED 상한, RATE 계산, 최소 금액 미달)
- UserCouponModel: use() 상태 전이, 이미 사용/만료 실패
- OrderDomainService: 쿠폰 적용 금액 계산, 재고 부족 실패

### 유스케이스/통합 테스트 (정합성)

- 존재하지 않는/타 유저/사용된/만료된 쿠폰으로 주문 → 실패 + 재고 미차감(롤백)
- 재고 부족 → 주문 실패 + 쿠폰 미사용(롤백)
- 주문 성공 → 재고 차감 + 쿠폰 USED + 스냅샷 금액 정확

### 동시성 테스트 (Testcontainers MySQL + ExecutorService/CountDownLatch)

1. **재고**: 재고 10, 20명 동시 주문(각 1개) → 성공 정확히 10건, 최종 재고 0, 음수 없음
2. **쿠폰**: 같은 쿠폰으로 N건 동시 주문 → 정확히 1건 성공, 실패 주문의 재고 롤백 확인
3. **좋아요**: N명 동시 좋아요/취소 → `like_count == COUNT(likes)`
4. **발급**: 같은 사용자 동시 발급 N건 → 1매만 발급

H2가 아닌 MySQL Testcontainers 필수 (락 동작 차이).

## 구현 원칙

- TDD: Red → Green → Refactor
- 구조 변경(syncStock 제거, 좋아요 핸들러 수정)과 기능 추가(쿠폰) 커밋 분리
- 비즈니스 실패는 `CoreException` + `ErrorType`
- Repository Port는 `domain/{domain}`, 구현은 `infrastructure/{domain}`

## 알려진 trade-off

- 템플릿 PUT 수정 시 이미 발급된 쿠폰의 할인 조건도 함께 변한다 (템플릿 참조 구조). 발급 시점 조건 보존이 필요해지면 발급 시 스냅샷으로 확장한다.
- 쿠폰 낙관적 락 충돌 시 재시도 없이 즉시 실패 — 같은 쿠폰 동시 사용은 비정상 시도이므로 의도된 동작.
- EXPIRED 파생 방식은 user_coupons 테이블만 봐서는 만료 여부를 알 수 없다 (항상 템플릿 조인 필요). 만료 상태 영속화가 필요해지면 배치로 확장한다.
