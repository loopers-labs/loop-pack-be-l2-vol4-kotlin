# 쿠폰 도메인 + 주문 트랜잭션/동시성 제어 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문 시 재고/주문/쿠폰의 정합성을 단일 트랜잭션으로 보장하고, 자원별 락(재고=비관적, 쿠폰=낙관적, 좋아요=원자적 UPDATE)으로 동시성 이슈를 제어한다.

**Architecture:** Clean Architecture 4레이어(`interfaces → application → domain ← infrastructure`). 쿠폰은 템플릿(`CouponModel`)과 발급 쿠폰(`UserCouponModel`)으로 분리. 주문 유스케이스는 단일 `@Transactional`에서 쿠폰 사용 + 재고 차감 + 주문 저장을 수행하고 실패 시 전체 롤백.

**Tech Stack:** Kotlin 2.0.20 / Spring Boot 3.4.4 / JPA / MySQL Testcontainers (테스트 프로필 `ddl-auto: create` — 스키마 마이그레이션 파일 불필요)

**Spec:** `docs/superpowers/specs/2026-06-12-coupon-order-concurrency-design.md`

**스펙 정정 1건:** 스펙은 `stockQuantity` 사용처를 "상품 상세뿐"이라 했으나, 실제로는 목록 조회(`GetProductsUsecase`)도 동일한 `ProductInfo`를 반환한다. 목록은 `findAllByProductIdIn` 배치 조회로 채운다 (Task 3).

---

## 전제 조건

- Docker 실행 중이어야 함 (MySQL Testcontainers).
- 테스트 실행: `./gradlew :apps:commerce-api:test --tests "<클래스명>"` (모듈 루트에서).
- 모든 커밋 전 `./gradlew :apps:commerce-api:ktlintCheck` 통과 필요 (pre-commit 훅이 자동 실행함).
- 기존 테스트 패턴: 유스케이스 단위 테스트는 인메모리 fake (`apps/commerce-api/src/test/kotlin/com/loopers/application/...` 내 `InMemoryXxxRepository`), 통합/E2E는 `@SpringBootTest` + `DatabaseCleanUp`(`@AfterEach`에서 truncate). 테스트 엔티티 ID 부여는 `com.loopers.domain.withId(id)` 헬퍼(테스트 소스 `domain/TestEntityId.kt`).
- 도메인 검증 실패는 `CoreException(ErrorType.XXX, "메시지")`.
- 사용자 fixture 규칙 (`UserModel` 검증): loginId 영숫자만, password 8~16자(생년월일 미포함), name 한글/영문/공백, email 형식. 예: `loginId = "user1"`, `password = "Password1!"`, `birthDate = LocalDate.of(1990, 1, 1)`, `email = "user1@loopers.com"`.

## 파일 구조 맵

```text
apps/commerce-api/src/main/kotlin/com/loopers/
  domain/coupon/
    CouponModel.kt            # 템플릿 엔티티 (FIXED/RATE, calculateDiscount)
    CouponType.kt
    UserCouponModel.kt        # 발급 쿠폰 (@Version 낙관적 락, use() 상태 전이)
    UserCouponStatus.kt
    CouponRepository.kt       # Port
    UserCouponRepository.kt   # Port
  infrastructure/coupon/
    CouponRepositoryImpl.kt / CouponJpaRepository.kt
    UserCouponRepositoryImpl.kt / UserCouponJpaRepository.kt
  application/coupon/
    CouponCommand.kt          # IssueCouponCommand, MyCouponsCommand
    CouponInfo.kt             # MyCouponInfo
    AdminCouponCommand.kt / AdminCouponInfo.kt / PageResult.kt
    usecase/IssueCouponUsecase.kt, GetMyCouponsUsecase.kt
    usecase/admin/CreateCouponUsecase.kt, UpdateCouponUsecase.kt, DeleteCouponUsecase.kt,
                  GetCouponUsecase.kt, GetCouponsUsecase.kt, GetCouponIssuesUsecase.kt
  interfaces/api/coupon/
    CouponV1Controller.kt / CouponV1Dto.kt              # 발급, 내 쿠폰
    AdminCouponV1Controller.kt / AdminCouponV1Dto.kt    # 템플릿 CRUD + 발급 내역
  (수정) domain/product/ProductModel.kt                  # stockQuantity 제거
  (수정) domain/product/ProductRepository.kt             # increment/decrementLikeCount 추가
  (수정) domain/product/ProductStockRepository.kt        # findByProductIdForUpdate, findAllByProductIdIn 추가
  (수정) domain/order/OrderModel.kt                      # discountAmount, paidPrice, userCouponId
  (수정) domain/order/OrderDomainService.kt              # 쿠폰 적용, syncStock 제거
  (수정) application/order/usecase/CreateOrderUsecase.kt # 쿠폰 + 비관적 락 + 정렬
  (수정) application/like/ProductLikeCountEventHandler.kt # REQUIRES_NEW + 원자적 UPDATE
  (수정) support/error/ErrorType.kt                      # UNAUTHORIZED 추가
  (수정) interfaces/api/ApiControllerAdvice.kt           # OptimisticLockingFailure → 409

apps/commerce-api/src/test/kotlin/com/loopers/
  support/ConcurrencyTestSupport.kt        # runConcurrently 헬퍼
  application/like/LikeConcurrencyTest.kt
  application/order/OrderStockConcurrencyTest.kt
  application/order/OrderCouponIntegrationTest.kt
  application/coupon/CouponConcurrencyTest.kt
  domain/coupon/CouponModelTest.kt, UserCouponModelTest.kt
  application/coupon/usecase/IssueCouponUsecaseTest.kt, GetMyCouponsUsecaseTest.kt
  application/coupon/InMemoryCouponRepository.kt, InMemoryUserCouponRepository.kt
  interfaces/api/CouponV1ApiE2ETest.kt, AdminCouponV1ApiE2ETest.kt

http/commerce-api/coupon-v1.http, order-v1.http
```

---

### Task 1: 동시성 테스트 헬퍼 + 좋아요 카운트 결함 재현 (Red)

**Files:**
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/support/ConcurrencyTestSupport.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/application/like/LikeConcurrencyTest.kt`

- [ ] **Step 1: 동시 실행 헬퍼 작성**

```kotlin
package com.loopers.support

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * threadCount개의 스레드가 동시에 task를 실행하도록 출발선(start latch)을 맞춘다.
 * task에서 던져진 예외를 모아 반환한다. (성공 수 = threadCount - 반환 리스트 크기)
 */
fun runConcurrently(threadCount: Int, task: (index: Int) -> Unit): List<Throwable> {
    val executor = Executors.newFixedThreadPool(threadCount)
    val ready = CountDownLatch(threadCount)
    val start = CountDownLatch(1)
    val done = CountDownLatch(threadCount)
    val errors = Collections.synchronizedList(mutableListOf<Throwable>())

    repeat(threadCount) { index ->
        executor.submit {
            ready.countDown()
            try {
                start.await()
                task(index)
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                done.countDown()
            }
        }
    }

    ready.await()
    start.countDown()
    done.await()
    executor.shutdown()
    return errors.toList()
}
```

- [ ] **Step 2: 좋아요 동시성 실패 테스트 작성**

현재 코드의 두 가지 결함을 재현한다: (1) `AFTER_COMMIT + @Transactional(REQUIRED)` 조합으로 likeCount 변경이 flush되지 않아 유실됨, (2) 읽기-수정-쓰기 lost update.

```kotlin
package com.loopers.application.like

import com.loopers.application.like.usecase.LikeProductCommand
import com.loopers.application.like.usecase.LikeProductUsecase
import com.loopers.application.like.usecase.UnlikeProductUsecase
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserService
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class LikeConcurrencyTest @Autowired constructor(
    private val likeProductUsecase: LikeProductUsecase,
    private val unlikeProductUsecase: UnlikeProductUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val likeRepository: LikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("여러 사용자가 동시에 좋아요를 눌러도 좋아요 수가 정확히 반영된다.")
    @Test
    fun likeCountIsAccurate_whenUsersLikeConcurrently() {
        // arrange
        val threadCount = 10
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        val users = (1..threadCount).map { signUp("liker$it") }

        // act
        val errors = runConcurrently(threadCount) { index ->
            likeProductUsecase.execute(
                LikeProductCommand(loginId = users[index].loginId, password = PASSWORD, productId = product.id),
            )
        }

        // assert
        assertAll(
            { assertThat(errors).isEmpty() },
            { assertThat(likeRepository.countByProductId(product.id)).isEqualTo(threadCount.toLong()) },
            { assertThat(productRepository.findActiveById(product.id)!!.likeCount).isEqualTo(threadCount) },
        )
    }

    @DisplayName("여러 사용자가 동시에 좋아요 취소를 해도 좋아요 수가 정확히 반영된다.")
    @Test
    fun likeCountIsAccurate_whenUsersUnlikeConcurrently() {
        // arrange
        val threadCount = 10
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        val users = (1..threadCount).map { signUp("unliker$it") }
        users.forEach {
            likeProductUsecase.execute(LikeProductCommand(loginId = it.loginId, password = PASSWORD, productId = product.id))
        }

        // act
        val errors = runConcurrently(threadCount) { index ->
            unlikeProductUsecase.execute(
                LikeProductCommand(loginId = users[index].loginId, password = PASSWORD, productId = product.id),
            )
        }

        // assert
        assertAll(
            { assertThat(errors).isEmpty() },
            { assertThat(likeRepository.countByProductId(product.id)).isEqualTo(0L) },
            { assertThat(productRepository.findActiveById(product.id)!!.likeCount).isEqualTo(0) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
```

참고: `LikeProductCommand`의 실제 프로퍼티명은 `application/like/usecase/LikeProductCommand.kt`(또는 `LikeProductUsecase.kt` 내 정의)를 열어 확인하고 맞춘다.

- [ ] **Step 3: 실패 확인 (Red)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.LikeConcurrencyTest"`
Expected: FAIL — `likeCount` 단언 실패 (AFTER_COMMIT 변경 유실로 0이 나오거나, lost update로 threadCount 미만)

- [ ] **Step 4: 커밋 (실패 테스트는 다음 Task에서 함께 커밋하므로 여기서는 헬퍼만 스테이징해두고 넘어가도 됨 — 단독 커밋하지 않는다)**

---

### Task 2: 좋아요 카운트 — 원자적 UPDATE + REQUIRES_NEW (Green)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/like/ProductLikeCountEventHandler.kt`
- Modify: 테스트의 `InMemoryProductRepository` (위치는 `grep -rn "class InMemoryProductRepository" apps/commerce-api/src/test`)
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/like/ProductLikeCountEventHandlerTest.kt`

- [ ] **Step 1: Port에 원자적 증감 메서드 추가**

`ProductRepository.kt`:

```kotlin
interface ProductRepository {
    fun save(product: ProductModel): ProductModel
    fun findActiveById(id: Long): ProductModel?
    fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel>
    fun existsActiveById(id: Long): Boolean
    fun incrementLikeCount(productId: Long)
    fun decrementLikeCount(productId: Long)
}
```

- [ ] **Step 2: JPA 구현 — 원자적 UPDATE 쿼리**

`ProductJpaRepository.kt`에 추가:

```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Modifying
@Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId AND p.deletedAt IS NULL")
fun incrementLikeCount(@Param("productId") productId: Long): Int

@Modifying
@Query(
    "UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 " +
        "WHERE p.id = :productId AND p.likeCount > 0 AND p.deletedAt IS NULL",
)
fun decrementLikeCount(@Param("productId") productId: Long): Int
```

`ProductRepositoryImpl.kt`에 추가:

```kotlin
override fun incrementLikeCount(productId: Long) {
    productJpaRepository.incrementLikeCount(productId)
}

override fun decrementLikeCount(productId: Long) {
    productJpaRepository.decrementLikeCount(productId)
}
```

- [ ] **Step 3: 이벤트 핸들러 — REQUIRES_NEW + 원자적 UPDATE 호출**

`ProductLikeCountEventHandler.kt` 전체 교체:

```kotlin
package com.loopers.application.like

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ProductLikeCountEventHandler(
    private val productRepository: ProductRepository,
) {
    // AFTER_COMMIT 시점에는 원본 트랜잭션이 이미 끝났으므로 REQUIRES_NEW로 새 트랜잭션을 연다.
    // (REQUIRED면 완료된 트랜잭션에 참여해 변경이 조용히 유실된다)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeCreatedEvent) {
        productRepository.incrementLikeCount(event.productId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeDeletedEvent) {
        productRepository.decrementLikeCount(event.productId)
    }
}
```

상품 존재 검증(NOT_FOUND throw)은 제거한다 — 커밋 이후라 예외는 로그만 남고, 없는 상품이면 UPDATE 영향 행 0건으로 무해하다. `ProductModel`의 `incrementLikeCount()/decrementLikeCount()` 행위 메서드는 인메모리 fake가 사용하므로 유지한다.

- [ ] **Step 4: 인메모리 fake와 핸들러 단위 테스트 갱신**

`InMemoryProductRepository`에 추가 (저장 구조가 `MutableMap<Long, ProductModel>`이 아니면 해당 구조에 맞게 같은 의미로 구현):

```kotlin
override fun incrementLikeCount(productId: Long) {
    findActiveById(productId)?.incrementLikeCount()
}

override fun decrementLikeCount(productId: Long) {
    findActiveById(productId)?.decrementLikeCount()
}
```

`ProductLikeCountEventHandlerTest.kt`: 기존 테스트가 "상품 없으면 NOT_FOUND" 케이스를 검증한다면 그 테스트는 "없는 상품이면 아무 일도 일어나지 않는다"로 의미를 바꿔 수정한다 (검증 삭제가 아니라 변경된 동작에 맞춘 갱신). 카운트 증감 검증 테스트는 그대로 통과해야 한다.

- [ ] **Step 5: 전체 확인 (Green)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.like.*"`
Expected: PASS — `LikeConcurrencyTest` 포함 전부 통과

- [ ] **Step 6: 커밋**

```bash
git add -A apps/commerce-api/src
git commit -m "fix: make like count updates atomic and run listener in new transaction"
```

---

### Task 3: syncStock 비정규화 제거 (구조 변경)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductModel.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductStockRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpaRepository.kt`, `ProductStockRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductInfo.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/usecase/GetProductsUsecase.kt`, `GetProductDetailUsecase.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderDomainService.kt`
- Modify: 컴파일 깨지는 테스트들 (아래 Step 5)

- [ ] **Step 1: ProductStockRepository에 배치 조회 추가**

```kotlin
interface ProductStockRepository {
    fun save(stock: ProductStockModel): ProductStockModel
    fun findByProductId(productId: Long): ProductStockModel?
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel>
}
```

`ProductStockJpaRepository.kt`에 추가:

```kotlin
fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel>
```

`ProductStockRepositoryImpl.kt`에 추가:

```kotlin
override fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel> {
    return productStockJpaRepository.findAllByProductIdIn(productIds)
}
```

- [ ] **Step 2: ProductModel에서 stockQuantity 제거**

`ProductModel.kt`에서 제거할 것: 생성자 파라미터 `stockQuantity: Int = 0`, `stockQuantity` 프로퍼티(@Column 포함), `syncStock()` 메서드, `validateStockQuantity()` 및 `validate()` 내 호출. `likeCount`와 `incrementLikeCount/decrementLikeCount`는 유지.

- [ ] **Step 3: 조회 유스케이스가 재고를 직접 조회하도록 변경**

`ProductInfo.kt` — `from` 시그니처 변경:

```kotlin
companion object {
    fun from(detail: ProductDetail, stockQuantity: Int): ProductInfo {
        return ProductInfo(
            id = detail.product.id,
            brand = Brand(
                id = detail.brand.id,
                name = detail.brand.name,
                description = detail.brand.description,
            ),
            name = detail.product.name,
            description = detail.product.description,
            price = detail.product.price,
            stockQuantity = stockQuantity,
            likeCount = detail.product.likeCount,
        )
    }
}
```

`GetProductDetailUsecase.kt` — 생성자에 `private val productStockRepository: ProductStockRepository` 추가, execute 마지막을:

```kotlin
val stockQuantity = productStockRepository.findByProductId(productId)?.quantity ?: 0

return productCatalogDomainService.getDetail(product = product, brand = brand)
    .let { ProductInfo.from(it, stockQuantity) }
```

`GetProductsUsecase.kt` — 생성자에 `private val productStockRepository: ProductStockRepository` 추가, 마지막 map을:

```kotlin
val stockByProductId = productStockRepository.findAllByProductIdIn(products.map { it.id })
    .associate { it.productId to it.quantity }

return productCatalogDomainService.getDetails(products = products, brandsById = brandsById)
    .map { ProductInfo.from(it, stockByProductId[it.product.id] ?: 0) }
```

- [ ] **Step 4: OrderDomainService에서 syncStock 호출 제거**

```kotlin
val orderItems = items.map {
    it.stock.deduct(it.quantity)
    it.product.toOrderItem(it.quantity)
}
```

- [ ] **Step 5: 컴파일 에러 정리 후 전체 테스트**

Run: `./gradlew :apps:commerce-api:compileTestKotlin` → 컴파일 에러가 난 테스트를 다음 규칙으로 수정한다:
- `ProductModel(..., stockQuantity = N)` → `stockQuantity` 인자 삭제 (재고 검증이 필요한 테스트면 `ProductStockModel` fixture를 사용)
- `ProductInfo.from(detail)` → `ProductInfo.from(detail, <기대 재고값>)`
- `syncStock` 호출/검증 → 삭제 (주문 후 `products.stock_quantity` 동기화 검증은 더 이상 유효하지 않은 스펙)
- 유스케이스 fixture에 `InMemoryProductStockRepository` 주입 추가

Run: `./gradlew :apps:commerce-api:test`
Expected: PASS (전체)

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: remove denormalized product stock quantity (syncStock)"
```

---

### Task 4: CouponModel 도메인 (TDD)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponType.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/coupon/CouponModelTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime

class CouponModelTest {
    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("정률 쿠폰의 할인율이 1~100 범위를 벗어나면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenRateIsOutOfRange() {
            listOf(BigDecimal.ZERO, BigDecimal("101")).forEach { rate ->
                val exception = assertThrows<CoreException> { coupon(type = CouponType.RATE, discountValue = rate) }
                assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            }
        }

        @DisplayName("정액 쿠폰의 할인 금액이 0 이하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenFixedAmountIsNotPositive() {
            val exception = assertThrows<CoreException> { coupon(type = CouponType.FIXED, discountValue = BigDecimal.ZERO) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            val exception = assertThrows<CoreException> { coupon(name = " ") }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("최소 주문 금액이 0 이하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenMinOrderAmountIsNotPositive() {
            val exception = assertThrows<CoreException> { coupon(minOrderAmount = BigDecimal.ZERO) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    inner class CalculateDiscount {
        @DisplayName("정액 쿠폰은 할인 금액을 그대로 반환하되 주문 금액을 초과하지 않는다.")
        @Test
        fun fixedDiscountIsCappedAtOrderAmount() {
            val fixed = coupon(type = CouponType.FIXED, discountValue = BigDecimal("5000"))

            assertThat(fixed.calculateDiscount(BigDecimal("20000"))).isEqualByComparingTo(BigDecimal("5000"))
            assertThat(fixed.calculateDiscount(BigDecimal("3000"))).isEqualByComparingTo(BigDecimal("3000"))
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 할인하고, 소수점 둘째 자리에서 내림한다.")
        @Test
        fun rateDiscountIsPercentageRoundedDown() {
            val rate = coupon(type = CouponType.RATE, discountValue = BigDecimal("10"))

            assertThat(rate.calculateDiscount(BigDecimal("27000"))).isEqualByComparingTo(BigDecimal("2700.00"))
            assertThat(rate.calculateDiscount(BigDecimal("99.99"))).isEqualByComparingTo(BigDecimal("9.99"))
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenOrderAmountIsBelowMinimum() {
            val withMinimum = coupon(minOrderAmount = BigDecimal("10000"))

            val exception = assertThrows<CoreException> { withMinimum.calculateDiscount(BigDecimal("9999")) }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("만료 여부를 판단할 때, expiredAt이 기준 시각보다 과거면 만료다.")
    @Test
    fun isExpired_whenExpiredAtIsBeforeNow() {
        val now = ZonedDateTime.now()
        assertThat(coupon(expiredAt = now.minusSeconds(1)).isExpired(now)).isTrue()
        assertThat(coupon(expiredAt = now.plusDays(1)).isExpired(now)).isFalse()
    }

    private fun coupon(
        name: String = "테스트 쿠폰",
        type: CouponType = CouponType.FIXED,
        discountValue: BigDecimal = BigDecimal("1000"),
        minOrderAmount: BigDecimal? = null,
        expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ) = CouponModel(
        name = name,
        type = type,
        discountValue = discountValue,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `CouponModel`, `CouponType` 미정의 컴파일 에러

- [ ] **Step 3: 구현**

`CouponType.kt`:

```kotlin
package com.loopers.domain.coupon

enum class CouponType {
    FIXED,
    RATE,
}
```

`CouponModel.kt`:

```kotlin
package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime

@Entity
@Table(name = "coupons")
class CouponModel(
    name: String,
    type: CouponType,
    discountValue: BigDecimal,
    minOrderAmount: BigDecimal?,
    expiredAt: ZonedDateTime,
) : BaseEntity() {
    @Column(nullable = false, length = 200)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: CouponType = type
        protected set

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    var discountValue: BigDecimal = discountValue
        protected set

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    var minOrderAmount: BigDecimal? = minOrderAmount
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime = expiredAt
        protected set

    init {
        validate(name = name, type = type, discountValue = discountValue, minOrderAmount = minOrderAmount)
    }

    fun calculateDiscount(orderAmount: BigDecimal): BigDecimal {
        minOrderAmount?.let {
            if (orderAmount < it) throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 만족하지 않습니다.")
        }
        return when (type) {
            CouponType.FIXED -> discountValue.min(orderAmount)
            CouponType.RATE -> orderAmount.multiply(discountValue)
                .divide(BigDecimal(100))
                .setScale(2, RoundingMode.DOWN)
        }
    }

    fun isExpired(now: ZonedDateTime): Boolean {
        return expiredAt.isBefore(now)
    }

    fun update(
        name: String,
        type: CouponType,
        discountValue: BigDecimal,
        minOrderAmount: BigDecimal?,
        expiredAt: ZonedDateTime,
    ) {
        validate(name = name, type = type, discountValue = discountValue, minOrderAmount = minOrderAmount)
        this.name = name
        this.type = type
        this.discountValue = discountValue
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    companion object {
        private fun validate(name: String, type: CouponType, discountValue: BigDecimal, minOrderAmount: BigDecimal?) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.")
            if (name.length > 200) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 200자를 초과할 수 없습니다.")
            when (type) {
                CouponType.FIXED ->
                    if (discountValue <= BigDecimal.ZERO) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.")
                    }
                CouponType.RATE ->
                    if (discountValue < BigDecimal.ONE || discountValue > BigDecimal(100)) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1~100 사이여야 합니다.")
                    }
            }
            if (minOrderAmount != null && minOrderAmount <= BigDecimal.ZERO) {
                throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0보다 커야 합니다.")
            }
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.coupon.CouponModelTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add -A apps/commerce-api/src
git commit -m "feat: add coupon template domain model"
```

---

### Task 5: UserCouponModel 도메인 (TDD)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/UserCouponStatus.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/UserCouponModel.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/coupon/UserCouponModelTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.loopers.domain.coupon

import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime

class UserCouponModelTest {
    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    inner class Use {
        @DisplayName("사용 가능한 쿠폰이면 USED로 전이되고 사용 시각이 기록된다.")
        @Test
        fun marksAsUsed_whenAvailable() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

            userCoupon.use(coupon = coupon, now = now)

            assertAll(
                { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
                { assertThat(userCoupon.usedAt).isEqualTo(now) },
            )
        }

        @DisplayName("이미 사용한 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadyUsed() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)
            userCoupon.use(coupon = coupon, now = now)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenExpired() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.minusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("다른 템플릿의 쿠폰으로 사용하려 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCouponMismatches() {
            val now = ZonedDateTime.now()
            val coupon = coupon(expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id + 1)

            val exception = assertThrows<CoreException> { userCoupon.use(coupon = coupon, now = now) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("현재 상태를 조회할 때, 미사용이지만 템플릿이 만료됐으면 EXPIRED를 반환한다.")
    @Test
    fun currentStatusIsExpired_whenAvailableButTemplateExpired() {
        val now = ZonedDateTime.now()
        val coupon = coupon(expiredAt = now.minusDays(1))
        val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id)

        assertThat(userCoupon.currentStatus(coupon = coupon, now = now)).isEqualTo(UserCouponStatus.EXPIRED)
    }

    private fun coupon(expiredAt: ZonedDateTime) = CouponModel(
        name = "테스트 쿠폰",
        type = CouponType.FIXED,
        discountValue = BigDecimal("1000"),
        minOrderAmount = null,
        expiredAt = expiredAt,
    ).withId(100L)
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `UserCouponModel`, `UserCouponStatus` 미정의

- [ ] **Step 3: 구현**

`UserCouponStatus.kt`:

```kotlin
package com.loopers.domain.coupon

/** EXPIRED는 저장되지 않고 조회 시 템플릿 만료 여부로 파생된다. */
enum class UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
```

`UserCouponModel.kt`:

```kotlin
package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class UserCouponModel(
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserCouponStatus = UserCouponStatus.AVAILABLE
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    // 낙관적 락: 동시 사용 시 한 트랜잭션만 커밋에 성공한다.
    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "회원 ID는 양수여야 합니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 양수여야 합니다.")
    }

    fun currentStatus(coupon: CouponModel, now: ZonedDateTime): UserCouponStatus {
        return when {
            status == UserCouponStatus.USED -> UserCouponStatus.USED
            coupon.isExpired(now) -> UserCouponStatus.EXPIRED
            else -> UserCouponStatus.AVAILABLE
        }
    }

    fun use(coupon: CouponModel, now: ZonedDateTime) {
        if (coupon.id != couponId) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 정보가 일치하지 않습니다.")
        when (currentStatus(coupon = coupon, now = now)) {
            UserCouponStatus.USED -> throw CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.")
            UserCouponStatus.EXPIRED -> throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.")
            UserCouponStatus.AVAILABLE -> {
                status = UserCouponStatus.USED
                usedAt = now
            }
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.coupon.UserCouponModelTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add -A apps/commerce-api/src
git commit -m "feat: add user coupon domain model with optimistic lock"
```

---

### Task 6: 쿠폰 Repository Port + Infrastructure

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponRepository.kt`, `UserCouponRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/coupon/CouponJpaRepository.kt`, `CouponRepositoryImpl.kt`, `UserCouponJpaRepository.kt`, `UserCouponRepositoryImpl.kt`

단순 위임 코드라 자체 단위 테스트는 만들지 않는다 — Task 7 이후의 E2E/통합 테스트가 실제 DB로 검증한다.

- [ ] **Step 1: Port 작성**

`CouponRepository.kt`:

```kotlin
package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CouponRepository {
    fun save(coupon: CouponModel): CouponModel
    fun findActiveById(id: Long): CouponModel?
    fun findAllActive(pageable: Pageable): Page<CouponModel>

    /** 발급 쿠폰 표시용 — 삭제된 템플릿도 포함해 조회한다. */
    fun findAllByIdIn(ids: List<Long>): List<CouponModel>
}
```

`UserCouponRepository.kt`:

```kotlin
package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserCouponRepository {
    fun save(userCoupon: UserCouponModel): UserCouponModel
    fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel?
    fun findAllByUserId(userId: Long): List<UserCouponModel>
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel>
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
```

참고: `Pageable/Page`는 Spring Data 타입이지만 기술 중립적 페이징 추상화로 보고 Port에 사용한다 (자체 페이징 타입 정의는 현재 요구 대비 과함 — YAGNI).

- [ ] **Step 2: Infrastructure 구현**

`CouponJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<CouponModel, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponModel?
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<CouponModel>
    fun findAllByIdIn(ids: List<Long>): List<CouponModel>
}
```

`CouponRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: CouponModel): CouponModel {
        return couponJpaRepository.save(coupon)
    }

    override fun findActiveById(id: Long): CouponModel? {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id)
    }

    override fun findAllActive(pageable: Pageable): Page<CouponModel> {
        return couponJpaRepository.findAllByDeletedAtIsNull(pageable)
    }

    override fun findAllByIdIn(ids: List<Long>): List<CouponModel> {
        return couponJpaRepository.findAllByIdIn(ids)
    }
}
```

`UserCouponJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponJpaRepository : JpaRepository<UserCouponModel, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel?
    fun findAllByUserId(userId: Long): List<UserCouponModel>
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel>
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
```

`UserCouponRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    // saveAndFlush: 유니크 제약(중복 발급) 위반을 커밋 시점이 아니라 호출 지점에서
    // DataIntegrityViolationException으로 받기 위함 (유스케이스가 CONFLICT로 변환)
    override fun save(userCoupon: UserCouponModel): UserCouponModel {
        return userCouponJpaRepository.saveAndFlush(userCoupon)
    }

    override fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel? {
        return userCouponJpaRepository.findByIdAndUserId(id = id, userId = userId)
    }

    override fun findAllByUserId(userId: Long): List<UserCouponModel> {
        return userCouponJpaRepository.findAllByUserId(userId)
    }

    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> {
        return userCouponJpaRepository.findAllByCouponId(couponId = couponId, pageable = pageable)
    }

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId = userId, couponId = couponId)
    }
}
```

- [ ] **Step 3: 컴파일 확인 후 커밋**

Run: `./gradlew :apps:commerce-api:compileKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add -A apps/commerce-api/src
git commit -m "feat: add coupon repository ports and JPA implementations"
```

---

### Task 7: 쿠폰 발급 — IssueCouponUsecase + API (TDD)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/CouponCommand.kt`, `CouponInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/IssueCouponUsecase.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/CouponV1Controller.kt`, `CouponV1Dto.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/InMemoryCouponRepository.kt`, `InMemoryUserCouponRepository.kt`, `usecase/IssueCouponUsecaseTest.kt`

- [ ] **Step 1: 인메모리 fake 작성 (테스트 인프라)**

`InMemoryCouponRepository.kt`:

```kotlin
package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.withId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class InMemoryCouponRepository : CouponRepository {
    private val store = mutableMapOf<Long, CouponModel>()
    private var sequence = 0L

    override fun save(coupon: CouponModel): CouponModel {
        val saved = if (coupon.id == 0L) coupon.withId(++sequence) else coupon
        store[saved.id] = saved
        return saved
    }

    override fun findActiveById(id: Long): CouponModel? {
        return store[id]?.takeIf { it.deletedAt == null }
    }

    override fun findAllActive(pageable: Pageable): Page<CouponModel> {
        val active = store.values.filter { it.deletedAt == null }.sortedByDescending { it.id }
        val content = active.drop(pageable.pageNumber * pageable.pageSize).take(pageable.pageSize)
        return PageImpl(content, pageable, active.size.toLong())
    }

    override fun findAllByIdIn(ids: List<Long>): List<CouponModel> {
        return ids.mapNotNull { store[it] }
    }
}
```

`InMemoryUserCouponRepository.kt`:

```kotlin
package com.loopers.application.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.withId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class InMemoryUserCouponRepository : UserCouponRepository {
    private val store = mutableMapOf<Long, UserCouponModel>()
    private var sequence = 0L

    override fun save(userCoupon: UserCouponModel): UserCouponModel {
        if (userCoupon.id == 0L && existsByUserIdAndCouponId(userCoupon.userId, userCoupon.couponId)) {
            throw DataIntegrityViolationException("uk_user_coupons_user_coupon violation")
        }
        val saved = if (userCoupon.id == 0L) userCoupon.withId(++sequence) else userCoupon
        store[saved.id] = saved
        return saved
    }

    override fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel? {
        return store[id]?.takeIf { it.userId == userId }
    }

    override fun findAllByUserId(userId: Long): List<UserCouponModel> {
        return store.values.filter { it.userId == userId }.sortedByDescending { it.id }
    }

    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> {
        val matched = store.values.filter { it.couponId == couponId }.sortedByDescending { it.id }
        val content = matched.drop(pageable.pageNumber * pageable.pageSize).take(pageable.pageSize)
        return PageImpl(content, pageable, matched.size.toLong())
    }

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean {
        return store.values.any { it.userId == userId && it.couponId == couponId }
    }
}
```

- [ ] **Step 2: 실패하는 유스케이스 테스트 작성**

`IssueCouponUsecaseTest.kt` — 기존 `CreateOrderUsecaseTest`처럼 user fake가 필요하다. `grep -rn "class InMemoryUserRepository" apps/commerce-api/src/test`로 기존 fake를 찾아 재사용한다 (다른 패키지면 import).

```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.InMemoryCouponRepository
import com.loopers.application.coupon.InMemoryUserCouponRepository
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

class IssueCouponUsecaseTest {
    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    inner class Execute {
        @DisplayName("유효한 템플릿이면 AVAILABLE 상태의 쿠폰이 발급된다.")
        @Test
        fun issuesCoupon_whenTemplateIsValid() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon()

            val issued = fixture.issueCouponUsecase.execute(fixture.command(coupon.id))

            assertAll(
                { assertThat(issued.couponId).isEqualTo(coupon.id) },
                { assertThat(issued.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("존재하지 않는 템플릿이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenTemplateDoesNotExist() {
            val fixture = Fixture()

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(999L)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("만료된 템플릿이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTemplateIsExpired() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon(expiredAt = ZonedDateTime.now().minusDays(1))

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(coupon.id)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이미 발급받은 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadyIssued() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon()
            fixture.issueCouponUsecase.execute(fixture.command(coupon.id))

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(coupon.id)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        val couponRepository = InMemoryCouponRepository()
        val userCouponRepository = InMemoryUserCouponRepository()
        val issueCouponUsecase = IssueCouponUsecase(
            userService = UserService(userRepository),
            couponRepository = couponRepository,
            userCouponRepository = userCouponRepository,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "tester",
                    rawPassword = "Password1!",
                    name = "테스터",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "tester@loopers.com",
                ),
            )
        }

        fun saveCoupon(expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(30)): CouponModel {
            return couponRepository.save(
                CouponModel(
                    name = "테스트 쿠폰",
                    type = CouponType.FIXED,
                    discountValue = BigDecimal("1000"),
                    minOrderAmount = null,
                    expiredAt = expiredAt,
                ),
            )
        }

        fun command(couponId: Long) = IssueCouponCommand(loginId = "tester", password = "Password1!", couponId = couponId)
    }
}
```

(`InMemoryUserRepository`의 실제 패키지/생성 방식은 기존 코드를 따른다.)

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `IssueCouponUsecase`, `IssueCouponCommand` 미정의

- [ ] **Step 4: 구현**

`CouponCommand.kt`:

```kotlin
package com.loopers.application.coupon

data class IssueCouponCommand(
    val loginId: String,
    val password: String,
    val couponId: Long,
)

data class MyCouponsCommand(
    val loginId: String,
    val password: String,
)
```

`CouponInfo.kt`:

```kotlin
package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class MyCouponInfo(
    val id: Long,
    val couponId: Long,
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
    val status: UserCouponStatus,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(userCoupon: UserCouponModel, coupon: CouponModel, now: ZonedDateTime): MyCouponInfo {
            return MyCouponInfo(
                id = userCoupon.id,
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                status = userCoupon.currentStatus(coupon = coupon, now = now),
                usedAt = userCoupon.usedAt,
            )
        }
    }
}
```

`usecase/IssueCouponUsecase.kt`:

```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.MyCouponInfo
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class IssueCouponUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional
    fun execute(command: IssueCouponCommand): MyCouponInfo {
        val now = ZonedDateTime.now()
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val coupon = couponRepository.findActiveById(command.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        if (coupon.isExpired(now)) throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.")
        if (userCouponRepository.existsByUserIdAndCouponId(userId = user.id, couponId = coupon.id)) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
        }

        val userCoupon = try {
            userCouponRepository.save(UserCouponModel(userId = user.id, couponId = coupon.id))
        } catch (e: DataIntegrityViolationException) {
            // 사전 중복 체크를 통과한 동시 요청이 유니크 제약에 걸린 경우
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
        }
        return MyCouponInfo.from(userCoupon = userCoupon, coupon = coupon, now = now)
    }
}
```

- [ ] **Step 5: 단위 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.coupon.usecase.IssueCouponUsecaseTest"`
Expected: PASS

- [ ] **Step 6: API 작성 (Controller + Dto)**

`CouponV1Dto.kt`:

```kotlin
package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.MyCouponInfo
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

class CouponV1Dto {
    data class MyCouponResponse(
        val id: Long,
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: ZonedDateTime,
        val status: UserCouponStatus,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: MyCouponInfo): MyCouponResponse {
                return MyCouponResponse(
                    id = info.id,
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.discountValue,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    status = info.status,
                    usedAt = info.usedAt,
                )
            }
        }
    }
}
```

`CouponV1Controller.kt` (내 쿠폰 목록 엔드포인트는 Task 8에서 추가):

```kotlin
package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponV1Controller(
    private val issueCouponUsecase: IssueCouponUsecase,
) {
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    fun issue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.MyCouponResponse> {
        return issueCouponUsecase.execute(IssueCouponCommand(loginId = loginId, password = password, couponId = couponId))
            .let { CouponV1Dto.MyCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
```

- [ ] **Step 7: E2E 테스트 작성 및 통과 확인**

`apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/CouponV1ApiE2ETest.kt` — 기존 `UserV1ApiE2ETest` 패턴(TestRestTemplate + DatabaseCleanUp). 케이스: 발급 성공 200 + status AVAILABLE / 중복 발급 409. 사용자는 `userService.signUp`, 템플릿은 `couponRepository.save`로 arrange.

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.coupon.CouponV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue — 발급 성공 시 AVAILABLE 쿠폰을 반환한다.")
    @Test
    fun issuesCoupon() {
        // arrange
        signUp()
        val coupon = saveCoupon()

        // act
        val response = issue(coupon.id)

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body!!.data!!.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue — 중복 발급이면 409를 반환한다.")
    @Test
    fun returnsConflict_whenAlreadyIssued() {
        // arrange
        signUp()
        val coupon = saveCoupon()
        issue(coupon.id)

        // act
        val response = issue(coupon.id)

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun issue(couponId: Long) = testRestTemplate.exchange(
        "/api/v1/coupons/$couponId/issue",
        HttpMethod.POST,
        HttpEntity(null, authHeaders()),
        object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponResponse>>() {},
    )

    private fun authHeaders() = HttpHeaders().apply {
        set("X-Loopers-LoginId", "tester")
        set("X-Loopers-LoginPw", "Password1!")
    }

    private fun signUp() = userService.signUp(
        UserService.SignUpCommand(
            loginId = "tester",
            password = "Password1!",
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "tester@loopers.com",
        ),
    )

    private fun saveCoupon() = couponRepository.save(
        CouponModel(
            name = "정액 쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal("1000"),
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )
}
```

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.CouponV1ApiE2ETest"`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add -A
git commit -m "feat: add coupon issue API"
```

---

### Task 8: 내 쿠폰 목록 — GetMyCouponsUsecase + API (TDD)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/GetMyCouponsUsecase.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/CouponV1Controller.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/usecase/GetMyCouponsUsecaseTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

핵심 검증: 상태 파생 (AVAILABLE / USED / 템플릿 만료 시 EXPIRED).

```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.InMemoryCouponRepository
import com.loopers.application.coupon.InMemoryUserCouponRepository
import com.loopers.application.coupon.MyCouponsCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

class GetMyCouponsUsecaseTest {
    @DisplayName("내 쿠폰 목록은 AVAILABLE / USED / EXPIRED 상태를 함께 반환한다.")
    @Test
    fun returnsDerivedStatuses() {
        // arrange
        val fixture = Fixture()
        val now = ZonedDateTime.now()
        val available = fixture.saveCoupon(expiredAt = now.plusDays(1))
        val expired = fixture.saveCoupon(expiredAt = now.minusDays(1))
        val usedTemplate = fixture.saveCoupon(expiredAt = now.plusDays(1))

        fixture.userCouponRepository.save(UserCouponModel(userId = 1L, couponId = available.id))
        fixture.userCouponRepository.save(UserCouponModel(userId = 1L, couponId = expired.id))
        fixture.userCouponRepository.save(
            UserCouponModel(userId = 1L, couponId = usedTemplate.id).apply { use(coupon = usedTemplate, now = now) },
        )

        // act
        val coupons = fixture.getMyCouponsUsecase.execute(MyCouponsCommand(loginId = "tester", password = "Password1!"))

        // assert
        val statusByCouponId = coupons.associate { it.couponId to it.status }
        assertThat(statusByCouponId).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                available.id to UserCouponStatus.AVAILABLE,
                expired.id to UserCouponStatus.EXPIRED,
                usedTemplate.id to UserCouponStatus.USED,
            ),
        )
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        val couponRepository = InMemoryCouponRepository()
        val userCouponRepository = InMemoryUserCouponRepository()
        val getMyCouponsUsecase = GetMyCouponsUsecase(
            userService = UserService(userRepository),
            couponRepository = couponRepository,
            userCouponRepository = userCouponRepository,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "tester",
                    rawPassword = "Password1!",
                    name = "테스터",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "tester@loopers.com",
                ),
            )
        }

        fun saveCoupon(expiredAt: ZonedDateTime): CouponModel {
            return couponRepository.save(
                CouponModel(
                    name = "쿠폰",
                    type = CouponType.FIXED,
                    discountValue = BigDecimal("1000"),
                    minOrderAmount = null,
                    expiredAt = expiredAt,
                ),
            )
        }
    }
}
```

주의: fake 유저의 id가 1이 되는지는 `InMemoryUserRepository` 구현에 따른다 — 기존 fake가 시퀀스를 시작하는 값에 맞춰 `userId`를 조정한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `GetMyCouponsUsecase` 미정의

- [ ] **Step 3: 구현**

```kotlin
package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.MyCouponInfo
import com.loopers.application.coupon.MyCouponsCommand
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetMyCouponsUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(command: MyCouponsCommand): List<MyCouponInfo> {
        val now = ZonedDateTime.now()
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val userCoupons = userCouponRepository.findAllByUserId(user.id)
        val couponsById = couponRepository.findAllByIdIn(userCoupons.map { it.couponId }.distinct())
            .associateBy { it.id }

        return userCoupons.map { userCoupon ->
            val coupon = couponsById[userCoupon.couponId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.")
            MyCouponInfo.from(userCoupon = userCoupon, coupon = coupon, now = now)
        }
    }
}
```

- [ ] **Step 4: Controller에 엔드포인트 추가**

`CouponV1Controller.kt` — 생성자에 `private val getMyCouponsUsecase: GetMyCouponsUsecase` 추가, 메서드 추가:

```kotlin
@GetMapping("/api/v1/users/me/coupons")
fun myCoupons(
    @RequestHeader("X-Loopers-LoginId") loginId: String,
    @RequestHeader("X-Loopers-LoginPw") password: String,
): ApiResponse<List<CouponV1Dto.MyCouponResponse>> {
    return getMyCouponsUsecase.execute(MyCouponsCommand(loginId = loginId, password = password))
        .map { CouponV1Dto.MyCouponResponse.from(it) }
        .let { ApiResponse.success(it) }
}
```

(import: `org.springframework.web.bind.annotation.GetMapping`, `com.loopers.application.coupon.MyCouponsCommand`, `com.loopers.application.coupon.usecase.GetMyCouponsUsecase`)

- [ ] **Step 5: 통과 확인 및 커밋**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.coupon.*"`
Expected: PASS

```bash
git add -A
git commit -m "feat: add my coupons API with derived status"
```

---

### Task 9: 어드민 쿠폰 템플릿 CRUD + 발급 내역 (E2E-first TDD)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/AdminCouponCommand.kt`, `AdminCouponInfo.kt`, `PageResult.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/coupon/usecase/admin/` 하위 6개 유스케이스
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/AdminCouponV1Controller.kt`, `AdminCouponV1Dto.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AdminCouponV1ApiE2ETest.kt`

유스케이스가 전부 얇은 위임이므로 개별 단위 테스트 대신 E2E로 한 번에 검증한다 (상태 파생/할인 계산 같은 규칙은 이미 도메인 테스트가 커버).

- [ ] **Step 1: ErrorType에 UNAUTHORIZED 추가**

```kotlin
UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.reasonPhrase, "인증이 필요합니다."),
```

- [ ] **Step 2: 실패하는 E2E 테스트 작성**

`AdminCouponV1ApiE2ETest.kt` 핵심 케이스:

```kotlin
package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.interfaces.api.coupon.AdminCouponV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("X-Loopers-Ldap 헤더가 없으면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_withoutLdapHeader() {
        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.GET,
            HttpEntity(null, HttpHeaders()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @DisplayName("쿠폰 템플릿 등록 → 상세 → 수정 → 삭제 흐름이 동작한다.")
    @Test
    fun crudRoundTrip() {
        // arrange & act: 등록
        val created = testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            HttpEntity(createRequest(), adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
        )
        val couponId = created.body!!.data!!.id

        // act: 수정
        val updated = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.PUT,
            HttpEntity(createRequest().copy(name = "변경된 쿠폰"), adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
        )

        // act: 삭제
        val deleted = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.DELETE,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

        // act: 삭제 후 상세 조회
        val detailAfterDelete = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.GET,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
        )

        // assert
        assertAll(
            { assertThat(created.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(updated.body!!.data!!.name).isEqualTo("변경된 쿠폰") },
            { assertThat(deleted.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(detailAfterDelete.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
        )
    }

    @DisplayName("쿠폰 템플릿 목록을 페이징으로 조회한다.")
    @Test
    fun listsCouponsWithPaging() {
        repeat(3) { index ->
            testRestTemplate.exchange(
                "/api-admin/v1/coupons",
                HttpMethod.POST,
                HttpEntity(createRequest().copy(name = "쿠폰$index"), adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )
        }

        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons?page=0&size=2",
            HttpMethod.GET,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponPageResponse>>() {},
        )

        assertAll(
            { assertThat(response.body!!.data!!.items).hasSize(2) },
            { assertThat(response.body!!.data!!.totalCount).isEqualTo(3L) },
        )
    }

    @DisplayName("특정 쿠폰의 발급 내역을 조회한다.")
    @Test
    fun listsIssuedCoupons() {
        val created = testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            HttpEntity(createRequest(), adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
        )
        val couponId = created.body!!.data!!.id
        userCouponRepository.save(UserCouponModel(userId = 1L, couponId = couponId))
        userCouponRepository.save(UserCouponModel(userId = 2L, couponId = couponId))

        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId/issues?page=0&size=20",
            HttpMethod.GET,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse>>() {},
        )

        assertThat(response.body!!.data!!.items).hasSize(2)
    }

    private fun adminHeaders() = HttpHeaders().apply { set("X-Loopers-Ldap", "admin.user") }

    private fun createRequest() = AdminCouponV1Dto.CouponUpsertRequest(
        name = "신규가입 10% 할인",
        type = com.loopers.domain.coupon.CouponType.RATE,
        value = BigDecimal("10"),
        minOrderAmount = BigDecimal("10000"),
        expiredAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59),
    )
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — 어드민 dto/controller 미정의

- [ ] **Step 4: application 레이어 구현**

`PageResult.kt`:

```kotlin
package com.loopers.application.coupon

import org.springframework.data.domain.Page

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
) {
    companion object {
        fun <S, T> from(page: Page<S>, mapper: (S) -> T): PageResult<T> {
            return PageResult(
                items = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalCount = page.totalElements,
            )
        }
    }
}
```

`AdminCouponCommand.kt`:

```kotlin
package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import java.math.BigDecimal
import java.time.ZonedDateTime

data class UpsertCouponCommand(
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
)
```

`AdminCouponInfo.kt`:

```kotlin
package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class AdminCouponInfo(
    val id: Long,
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
) {
    companion object {
        fun from(coupon: CouponModel): AdminCouponInfo {
            return AdminCouponInfo(
                id = coupon.id,
                name = coupon.name,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
        }
    }
}

data class CouponIssueInfo(
    val id: Long,
    val userId: Long,
    val status: UserCouponStatus,
    val issuedAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(userCoupon: UserCouponModel, coupon: CouponModel, now: ZonedDateTime): CouponIssueInfo {
            return CouponIssueInfo(
                id = userCoupon.id,
                userId = userCoupon.userId,
                status = userCoupon.currentStatus(coupon = coupon, now = now),
                issuedAt = userCoupon.createdAt,
                usedAt = userCoupon.usedAt,
            )
        }
    }
}
```

`usecase/admin/` 6개 유스케이스 (패키지 공통: `package com.loopers.application.coupon.usecase.admin`):

```kotlin
// CreateCouponUsecase.kt
@Component
class CreateCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun execute(command: UpsertCouponCommand): AdminCouponInfo {
        val coupon = CouponModel(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return AdminCouponInfo.from(couponRepository.save(coupon))
    }
}

// GetCouponsUsecase.kt
@Component
class GetCouponsUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(page: Int, size: Int): PageResult<AdminCouponInfo> {
        val coupons = couponRepository.findAllActive(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
        return PageResult.from(coupons) { AdminCouponInfo.from(it) }
    }
}

// GetCouponUsecase.kt
@Component
class GetCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(couponId: Long): AdminCouponInfo {
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        return AdminCouponInfo.from(coupon)
    }
}

// UpdateCouponUsecase.kt
@Component
class UpdateCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun execute(couponId: Long, command: UpsertCouponCommand): AdminCouponInfo {
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        coupon.update(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return AdminCouponInfo.from(coupon)
    }
}

// DeleteCouponUsecase.kt
@Component
class DeleteCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun execute(couponId: Long) {
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        coupon.delete()
    }
}

// GetCouponIssuesUsecase.kt
@Component
class GetCouponIssuesUsecase(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(couponId: Long, page: Int, size: Int): PageResult<CouponIssueInfo> {
        val now = ZonedDateTime.now()
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        val issues = userCouponRepository.findAllByCouponId(
            couponId = couponId,
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")),
        )
        return PageResult.from(issues) { CouponIssueInfo.from(userCoupon = it, coupon = coupon, now = now) }
    }
}
```

(각 파일에 필요한 import: `com.loopers.application.coupon.*`, `com.loopers.domain.coupon.*`, `com.loopers.support.error.*`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Sort`, `org.springframework.stereotype.Component`, `org.springframework.transaction.annotation.Transactional`, `java.time.ZonedDateTime`)

`DeleteCouponUsecase`의 `coupon.delete()`는 BaseEntity의 soft delete — 트랜잭션 내 managed 엔티티이므로 변경 감지로 반영된다 (기존 `UserService.changePassword` 패턴과 동일).

- [ ] **Step 5: interfaces 레이어 구현**

`AdminCouponV1Dto.kt`:

```kotlin
package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.CouponIssueInfo
import com.loopers.application.coupon.PageResult
import com.loopers.application.coupon.UpsertCouponCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AdminCouponV1Dto {
    data class CouponUpsertRequest(
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): UpsertCouponCommand {
            return UpsertCouponCommand(
                name = name,
                type = type,
                discountValue = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt.atZone(ZoneId.systemDefault()),
            )
        }
    }

    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: AdminCouponInfo): CouponResponse {
                return CouponResponse(
                    id = info.id,
                    name = info.name,
                    type = info.type,
                    value = info.discountValue,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                )
            }
        }
    }

    data class CouponPageResponse(
        val items: List<CouponResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(result: PageResult<AdminCouponInfo>): CouponPageResponse {
                return CouponPageResponse(
                    items = result.items.map { CouponResponse.from(it) },
                    page = result.page,
                    size = result.size,
                    totalCount = result.totalCount,
                )
            }
        }
    }

    data class CouponIssueResponse(
        val id: Long,
        val userId: Long,
        val status: UserCouponStatus,
        val issuedAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponIssueInfo): CouponIssueResponse {
                return CouponIssueResponse(
                    id = info.id,
                    userId = info.userId,
                    status = info.status,
                    issuedAt = info.issuedAt,
                    usedAt = info.usedAt,
                )
            }
        }
    }

    data class CouponIssuePageResponse(
        val items: List<CouponIssueResponse>,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(result: PageResult<CouponIssueInfo>): CouponIssuePageResponse {
                return CouponIssuePageResponse(
                    items = result.items.map { CouponIssueResponse.from(it) },
                    page = result.page,
                    size = result.size,
                    totalCount = result.totalCount,
                )
            }
        }
    }
}
```

`AdminCouponV1Controller.kt`:

```kotlin
package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.usecase.admin.CreateCouponUsecase
import com.loopers.application.coupon.usecase.admin.DeleteCouponUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponIssuesUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponsUsecase
import com.loopers.application.coupon.usecase.admin.UpdateCouponUsecase
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponV1Controller(
    private val createCouponUsecase: CreateCouponUsecase,
    private val updateCouponUsecase: UpdateCouponUsecase,
    private val deleteCouponUsecase: DeleteCouponUsecase,
    private val getCouponUsecase: GetCouponUsecase,
    private val getCouponsUsecase: GetCouponsUsecase,
    private val getCouponIssuesUsecase: GetCouponIssuesUsecase,
) {
    @GetMapping
    fun list(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminCouponV1Dto.CouponPageResponse> {
        authorize(ldap)
        return getCouponsUsecase.execute(page = page, size = size)
            .let { AdminCouponV1Dto.CouponPageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{couponId}")
    fun detail(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return getCouponUsecase.execute(couponId)
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    fun create(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @RequestBody request: AdminCouponV1Dto.CouponUpsertRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return createCouponUsecase.execute(request.toCommand())
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{couponId}")
    fun update(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
        @RequestBody request: AdminCouponV1Dto.CouponUpsertRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return updateCouponUsecase.execute(couponId = couponId, command = request.toCommand())
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    fun delete(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        authorize(ldap)
        deleteCouponUsecase.execute(couponId)
        return ApiResponse.success()
    }

    @GetMapping("/{couponId}/issues")
    fun issues(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse> {
        authorize(ldap)
        return getCouponIssuesUsecase.execute(couponId = couponId, page = page, size = size)
            .let { AdminCouponV1Dto.CouponIssuePageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    // 어드민 인증 인프라가 없으므로 헤더 존재 검증만 하는 최소 구현 (설계 문서의 비목표 참고)
    private fun authorize(ldap: String?) {
        if (ldap.isNullOrBlank()) throw CoreException(ErrorType.UNAUTHORIZED, "관리자 인증이 필요합니다.")
    }

    companion object {
        private const val LDAP_HEADER = "X-Loopers-Ldap"
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.AdminCouponV1ApiE2ETest"`
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "feat: add admin coupon template CRUD and issue history APIs"
```

---

### Task 10: 주문 도메인 — 쿠폰 적용 금액 스냅샷 (TDD)

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderModel.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderDomainService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderModelTest.kt`, `OrderDomainServiceTest.kt` (추가)

- [ ] **Step 1: 실패하는 테스트 작성**

`OrderModelTest.kt`에 추가:

```kotlin
@DisplayName("쿠폰을 적용할 때,")
@Nested
inner class ApplyCoupon {
    @DisplayName("할인 금액과 최종 결제 금액이 스냅샷으로 기록된다.")
    @Test
    fun recordsDiscountSnapshot() {
        val order = order() // 기존 테스트의 주문 fixture 헬퍼 사용 (totalPrice가 0보다 큰 주문)

        order.applyCoupon(userCouponId = 1L, discountAmount = BigDecimal("1000"))

        assertAll(
            { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000")) },
            { assertThat(order.paidPrice).isEqualByComparingTo(order.totalPrice - BigDecimal("1000")) },
            { assertThat(order.userCouponId).isEqualTo(1L) },
        )
    }

    @DisplayName("할인 금액이 주문 금액을 초과하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenDiscountExceedsTotal() {
        val order = order()

        val exception = assertThrows<CoreException> {
            order.applyCoupon(userCouponId = 1L, discountAmount = order.totalPrice + BigDecimal.ONE)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("쿠폰 미적용 주문의 최종 결제 금액은 주문 금액과 같다.")
    @Test
    fun paidPriceEqualsTotalPrice_withoutCoupon() {
        val order = order()

        assertAll(
            { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal.ZERO) },
            { assertThat(order.paidPrice).isEqualByComparingTo(order.totalPrice) },
            { assertThat(order.userCouponId).isNull() },
        )
    }
}
```

(`order()` fixture 헬퍼가 없으면 기존 테스트의 OrderModel 생성 방식을 따라 private 헬퍼로 추출한다.)

`OrderDomainServiceTest.kt`에 추가:

```kotlin
@DisplayName("쿠폰과 함께 주문을 생성하면 할인 적용 + 쿠폰 사용 처리가 함께 일어난다.")
@Test
fun appliesCouponAndMarksItUsed() {
    // arrange — 기존 테스트의 OrderProduct fixture 패턴 재사용
    val now = ZonedDateTime.now()
    val coupon = CouponModel(
        name = "정액 쿠폰",
        type = CouponType.FIXED,
        discountValue = BigDecimal("1000"),
        minOrderAmount = null,
        expiredAt = now.plusDays(1),
    ).withId(100L)
    val userCoupon = UserCouponModel(userId = 1L, couponId = 100L)

    // act
    val order = orderDomainService.create(
        userId = 1L,
        items = listOf(orderProduct()), // 기존 fixture 헬퍼
        couponApplication = OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon),
        now = now,
    )

    // assert
    assertAll(
        { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000")) },
        { assertThat(order.paidPrice).isEqualByComparingTo(order.totalPrice - BigDecimal("1000")) },
        { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
    )
}

@DisplayName("최소 주문 금액 미달 쿠폰으로 주문을 생성하면 BAD_REQUEST 예외가 발생한다.")
@Test
fun throwsBadRequest_whenOrderAmountBelowCouponMinimum() {
    val now = ZonedDateTime.now()
    val coupon = CouponModel(
        name = "조건부 쿠폰",
        type = CouponType.FIXED,
        discountValue = BigDecimal("1000"),
        minOrderAmount = BigDecimal("99999999"),
        expiredAt = now.plusDays(1),
    ).withId(100L)
    val userCoupon = UserCouponModel(userId = 1L, couponId = 100L)

    val exception = assertThrows<CoreException> {
        orderDomainService.create(
            userId = 1L,
            items = listOf(orderProduct()),
            couponApplication = OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon),
            now = now,
        )
    }

    assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `applyCoupon`, `CouponApplication` 미정의

- [ ] **Step 3: OrderModel 구현**

`OrderModel.kt`에 필드/메서드 추가:

```kotlin
@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
var discountAmount: BigDecimal = BigDecimal.ZERO
    protected set

@Column(name = "paid_price", nullable = false, precision = 12, scale = 2)
var paidPrice: BigDecimal = BigDecimal.ZERO
    protected set

@Column(name = "user_coupon_id")
var userCouponId: Long? = null
    protected set
```

`init` 블록 마지막 줄(totalPrice 계산 직후)에 추가:

```kotlin
paidPrice = totalPrice
```

메서드 추가:

```kotlin
fun applyCoupon(userCouponId: Long, discountAmount: BigDecimal) {
    if (status != OrderStatus.PENDING) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰을 적용할 수 없는 주문 상태입니다.")
    if (discountAmount < BigDecimal.ZERO) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 음수일 수 없습니다.")
    if (discountAmount > totalPrice) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.")
    this.userCouponId = userCouponId
    this.discountAmount = discountAmount
    this.paidPrice = totalPrice - discountAmount
}
```

- [ ] **Step 4: OrderDomainService 구현**

`OrderDomainService.kt` 전체 교체:

```kotlin
package com.loopers.domain.order

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductStockModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

class OrderDomainService {
    fun create(
        userId: Long,
        items: List<OrderProduct>,
        couponApplication: CouponApplication? = null,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): OrderModel {
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.")
        if (items.map { it.product.id }.distinct().size != items.size) {
            throw CoreException(ErrorType.BAD_REQUEST, "하나의 주문에 같은 상품을 중복으로 담을 수 없습니다.")
        }

        items.forEach {
            if (!it.stock.hasEnough(it.quantity)) {
                throw CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.")
            }
        }

        val orderItems = items.map {
            it.stock.deduct(it.quantity)
            it.product.toOrderItem(it.quantity)
        }

        val order = OrderModel(
            userId = userId,
            items = orderItems,
        )

        couponApplication?.let {
            val discount = it.coupon.calculateDiscount(order.totalPrice)
            it.userCoupon.use(coupon = it.coupon, now = now)
            order.applyCoupon(userCouponId = it.userCoupon.id, discountAmount = discount)
        }

        return order
    }

    data class OrderProduct(
        val product: ProductModel,
        val stock: ProductStockModel,
        val quantity: Int,
    )

    data class CouponApplication(
        val coupon: CouponModel,
        val userCoupon: UserCouponModel,
    )
}
```

- [ ] **Step 5: OrderInfo에 금액 필드 추가**

`OrderInfo.kt` — 필드 추가 및 `from` 갱신:

```kotlin
data class OrderInfo(
    val id: Long,
    val userId: Long,
    val status: OrderStatus,
    val totalPrice: BigDecimal,
    val discountAmount: BigDecimal,
    val paidPrice: BigDecimal,
    val items: List<Item>,
)
```

`from(...)`의 생성 인자에 추가:

```kotlin
discountAmount = order.discountAmount,
paidPrice = order.paidPrice,
```

- [ ] **Step 6: 통과 확인 및 커밋**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.*"`
Expected: PASS

```bash
git add -A
git commit -m "feat: add coupon discount snapshot to order domain"
```

---

### Task 11: 재고 비관적 락 (동시성 테스트 Red → Green)

**Files:**
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderStockConcurrencyTest.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductStockRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductStockJpaRepository.kt`, `ProductStockRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/usecase/CreateOrderUsecase.kt`
- Modify: 테스트의 `InMemoryProductStockRepository`

- [ ] **Step 1: 재고 동시성 실패 테스트 작성 (Red)**

```kotlin
package com.loopers.application.order

import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class OrderStockConcurrencyTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일 상품에 동시 주문이 몰려도 재고는 정확히 차감되고 오버셀이 발생하지 않는다.")
    @Test
    fun deductsStockExactly_withoutOversell() {
        // arrange: 재고 5, 10명이 동시에 1개씩 주문 → 정확히 5건 성공
        val threadCount = 10
        val initialStock = 5
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = initialStock))
        val users = (1..threadCount).map { signUp("buyer$it") }

        // act
        val errors = runConcurrently(threadCount) { index ->
            createOrderUsecase.execute(
                OrderCommand(
                    loginId = users[index].loginId,
                    password = PASSWORD,
                    items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = 1)),
                ),
            )
        }

        // assert
        val finalStock = productStockRepository.findByProductId(product.id)!!.quantity
        assertAll(
            { assertThat(errors).hasSize(threadCount - initialStock) },
            { assertThat(errors).allSatisfy { assertThat((it as CoreException).errorType).isEqualTo(ErrorType.CONFLICT) } },
            { assertThat(finalStock).isEqualTo(0) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "구매자",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
```

주의: `OrderCommand`에는 Task 12에서 `couponId`가 추가되지만 기본값 nullable이므로 이 테스트는 그대로 유효하다.

- [ ] **Step 2: 실패 확인 (Red)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderStockConcurrencyTest"`
Expected: FAIL — lost update로 성공 건수 > 5 또는 최종 재고 ≠ 0

- [ ] **Step 3: 비관적 락 Port/구현 추가**

`ProductStockRepository.kt`에 추가:

```kotlin
/** 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 재고를 조회한다. 트랜잭션 내에서만 호출해야 한다. */
fun findByProductIdForUpdate(productId: Long): ProductStockModel?
```

`ProductStockJpaRepository.kt`에 추가:

```kotlin
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM ProductStockModel s WHERE s.productId = :productId")
fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductStockModel?
```

`ProductStockRepositoryImpl.kt`에 추가:

```kotlin
override fun findByProductIdForUpdate(productId: Long): ProductStockModel? {
    return productStockJpaRepository.findByProductIdForUpdate(productId)
}
```

테스트의 `InMemoryProductStockRepository`에 추가:

```kotlin
override fun findByProductIdForUpdate(productId: Long): ProductStockModel? {
    return findByProductId(productId)
}
```

- [ ] **Step 4: CreateOrderUsecase — 정렬 + 락 조회로 변경**

`execute` 내 주문 상품 조회 블록을 다음으로 교체:

```kotlin
val orderProducts = command.items
    .sortedBy { it.productId } // 락 획득 순서 고정 — 교차 주문 데드락 방지
    .map { item ->
        val product = productRepository.findActiveById(item.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val stock = productStockRepository.findByProductIdForUpdate(item.productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

        OrderDomainService.OrderProduct(
            product = product,
            stock = stock,
            quantity = item.quantity,
        )
    }
```

- [ ] **Step 5: 통과 확인 (Green)**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.*"`
Expected: PASS — 동시성 테스트 포함 전부

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "feat: prevent stock oversell with pessimistic lock and ordered acquisition"
```

---

### Task 12: CreateOrderUsecase 쿠폰 통합 + 예외 변환 + 정합성 통합 테스트

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCommand.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/usecase/CreateOrderUsecase.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/usecase/CreateOrderUsecaseTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCouponIntegrationTest.kt`

- [ ] **Step 1: 실패하는 유스케이스 단위 테스트 추가**

`CreateOrderUsecaseTest.kt`의 `Execute`에 추가 (Fixture에 `InMemoryCouponRepository`/`InMemoryUserCouponRepository` 주입과 쿠폰 헬퍼도 함께 추가):

```kotlin
@DisplayName("쿠폰을 적용하면 할인 금액이 반영되고 쿠폰은 사용 처리된다.")
@Test
fun appliesCouponDiscount() {
    // arrange
    val fixture = Fixture()
    val userCoupon = fixture.issueCoupon(discountValue = BigDecimal("1000"))

    // act
    val order = fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))

    // assert
    assertAll(
        { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000")) },
        { assertThat(order.paidPrice).isEqualByComparingTo(order.totalPrice - BigDecimal("1000")) },
        { assertThat(fixture.userCouponRepository.findByIdAndUserId(userCoupon.id, fixture.userId)!!.status).isEqualTo(UserCouponStatus.USED) },
    )
}

@DisplayName("내 소유가 아닌 쿠폰이면 NOT_FOUND 예외가 발생한다.")
@Test
fun throwsNotFound_whenCouponBelongsToOtherUser() {
    // arrange
    val fixture = Fixture()
    val otherUserCoupon = fixture.issueCouponToOtherUser()

    // act
    val exception = assertThrows<CoreException> {
        fixture.createOrderUsecase.execute(fixture.command(couponId = otherUserCoupon.id))
    }

    // assert
    assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
}

@DisplayName("이미 사용한 쿠폰이면 CONFLICT 예외가 발생한다.")
@Test
fun throwsConflict_whenCouponAlreadyUsed() {
    // arrange
    val fixture = Fixture()
    val userCoupon = fixture.issueCoupon(discountValue = BigDecimal("1000"))
    fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))

    // act
    val exception = assertThrows<CoreException> {
        fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))
    }

    // assert
    assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
}
```

Fixture에 추가할 내용 (기존 Fixture 구조에 맞춰 적용):

```kotlin
val couponRepository = InMemoryCouponRepository()
val userCouponRepository = InMemoryUserCouponRepository()
val userId = 1L // 기존 fixture의 사용자 id에 맞춤

// createOrderUsecase 생성자에 couponRepository, userCouponRepository 추가

fun issueCoupon(discountValue: BigDecimal): UserCouponModel {
    val coupon = couponRepository.save(
        CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = discountValue,
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )
    return userCouponRepository.save(UserCouponModel(userId = userId, couponId = coupon.id))
}

fun issueCouponToOtherUser(): UserCouponModel {
    val coupon = couponRepository.save(
        CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal("1000"),
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )
    return userCouponRepository.save(UserCouponModel(userId = userId + 999, couponId = coupon.id))
}

// 기존 command() 헬퍼에 couponId 파라미터 추가:
fun command(items: List<OrderCommand.OrderItemCommand> = defaultItems(), couponId: Long? = null) = OrderCommand(
    loginId = "...기존값...",
    password = "...기존값...",
    items = items,
    couponId = couponId,
)
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :apps:commerce-api:compileTestKotlin`
Expected: FAIL — `OrderCommand.couponId` 미정의

- [ ] **Step 3: 구현**

`OrderCommand.kt`:

```kotlin
data class OrderCommand(
    val loginId: String,
    val password: String,
    val items: List<OrderItemCommand>,
    val couponId: Long? = null,
) {
    data class OrderItemCommand(
        val productId: Long,
        val quantity: Int,
    )
}
```

`CreateOrderUsecase.kt` 전체 교체:

```kotlin
package com.loopers.application.order.usecase

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderInfo
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderDomainService
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class CreateOrderUsecase(
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderRepository: OrderRepository,
) {
    private val orderDomainService = OrderDomainService()

    /**
     * 단일 트랜잭션: 쿠폰 사용 + 재고 차감 + 주문 저장.
     * 하나라도 실패하면 전체 롤백된다.
     * - 재고: 비관적 락 (productId 오름차순으로 획득해 데드락 방지)
     * - 쿠폰: 낙관적 락 (@Version) — 동시 사용 충돌은 커밋 시점에 발생하며
     *   ApiControllerAdvice가 OptimisticLockingFailureException을 409로 변환한다.
     */
    @Transactional
    fun execute(command: OrderCommand): OrderInfo {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val couponApplication = command.couponId?.let { findCouponApplication(userCouponId = it, userId = user.id) }

        val orderProducts = command.items
            .sortedBy { it.productId } // 락 획득 순서 고정 — 교차 주문 데드락 방지
            .map { item ->
                val product = productRepository.findActiveById(item.productId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
                val stock = productStockRepository.findByProductIdForUpdate(item.productId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

                OrderDomainService.OrderProduct(
                    product = product,
                    stock = stock,
                    quantity = item.quantity,
                )
            }

        return orderDomainService.create(
            userId = user.id,
            items = orderProducts,
            couponApplication = couponApplication,
            now = ZonedDateTime.now(),
        )
            .let { orderRepository.save(it) }
            .let { OrderInfo.from(it) }
    }

    private fun findCouponApplication(userCouponId: Long, userId: Long): OrderDomainService.CouponApplication {
        // 타 유저 소유 쿠폰도 NOT_FOUND — 존재 여부를 노출하지 않는다.
        val userCoupon = userCouponRepository.findByIdAndUserId(id = userCouponId, userId = userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        val coupon = couponRepository.findActiveById(userCoupon.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        return OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon)
    }
}
```

`OrderV1Dto.kt` — `OrderRequest`에 `couponId` 추가, `OrderResponse`에 금액 필드 추가:

```kotlin
data class OrderRequest(
    val items: List<OrderItemRequest>,
    val couponId: Long? = null,
) {
    fun toCommand(loginId: String, password: String): OrderCommand {
        return OrderCommand(
            loginId = loginId,
            password = password,
            items = items.map {
                OrderCommand.OrderItemCommand(
                    productId = it.productId,
                    quantity = it.quantity,
                )
            },
            couponId = couponId,
        )
    }
}
```

`OrderResponse`에 필드 `val discountAmount: BigDecimal, val paidPrice: BigDecimal` 추가하고 `from`에 `discountAmount = info.discountAmount, paidPrice = info.paidPrice` 추가.

`ApiControllerAdvice.kt`에 핸들러 추가:

```kotlin
import org.springframework.dao.OptimisticLockingFailureException

@ExceptionHandler
fun handleConflict(e: OptimisticLockingFailureException): ResponseEntity<ApiResponse<*>> {
    log.warn("OptimisticLockingFailureException : {}", e.message)
    return failureResponse(errorType = ErrorType.CONFLICT, errorMessage = "동시 요청으로 처리에 실패했습니다. 다시 시도해주세요.")
}
```

(스펙은 "usecase에서 try-catch를 기본"이라 했지만, `@Version` 충돌은 유스케이스 메서드 반환 후 커밋 시점에 발생하므로 유스케이스 안에서 잡을 수 없다 — 공통 핸들러 방식으로 확정한다.)

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.usecase.CreateOrderUsecaseTest"`
Expected: PASS

- [ ] **Step 5: 정합성 통합 테스트 작성 (롤백 검증)**

`OrderCouponIntegrationTest.kt`:

```kotlin
package com.loopers.application.order

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class OrderCouponIntegrationTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val issueCouponUsecase: IssueCouponUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private var productId = 0L
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        val user = userService.signUp(
            UserService.SignUpCommand(
                loginId = "tester",
                password = "Password1!",
                name = "테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "tester@loopers.com",
            ),
        )
        userId = user.id
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productId = product.id
        productStockRepository.save(ProductStockModel(productId = productId, quantity = 10))
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 주문 성공 시 재고 차감 + 쿠폰 사용 + 금액 스냅샷이 모두 반영된다.")
    @Test
    fun commitsAllChanges_whenOrderSucceeds() {
        // arrange
        val userCouponId = issueCoupon(BigDecimal("3000"))

        // act
        val order = createOrderUsecase.execute(command(quantity = 2, couponId = userCouponId))

        // assert
        assertAll(
            { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("20000")) },
            { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("3000")) },
            { assertThat(order.paidPrice).isEqualByComparingTo(BigDecimal("17000")) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(8) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, userId)!!.status).isEqualTo(UserCouponStatus.USED) },
        )
    }

    @DisplayName("재고가 부족하면 주문이 실패하고 쿠폰은 사용되지 않은 채 남는다 (롤백).")
    @Test
    fun rollsBackCoupon_whenStockIsInsufficient() {
        // arrange
        val userCouponId = issueCoupon(BigDecimal("3000"))

        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 999, couponId = userCouponId))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(10) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, userId)!!.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    @DisplayName("이미 사용한 쿠폰으로 주문하면 실패하고 재고는 차감되지 않는다 (롤백).")
    @Test
    fun rollsBackStock_whenCouponAlreadyUsed() {
        // arrange — 첫 주문으로 쿠폰 소진
        val userCouponId = issueCoupon(BigDecimal("3000"))
        createOrderUsecase.execute(command(quantity = 1, couponId = userCouponId))

        // act
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 1, couponId = userCouponId))
        }

        // assert
        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(9) },
        )
    }

    @DisplayName("존재하지 않는 쿠폰으로 주문하면 NOT_FOUND로 실패한다.")
    @Test
    fun failsOrder_whenCouponDoesNotExist() {
        val exception = assertThrows<CoreException> {
            createOrderUsecase.execute(command(quantity = 1, couponId = 99999L))
        }

        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND) },
            { assertThat(productStockRepository.findByProductId(productId)!!.quantity).isEqualTo(10) },
        )
    }

    private fun issueCoupon(discountValue: BigDecimal): Long {
        val coupon = couponRepository.save(
            CouponModel(
                name = "쿠폰",
                type = CouponType.FIXED,
                discountValue = discountValue,
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(30),
            ),
        )
        return issueCouponUsecase.execute(
            IssueCouponCommand(loginId = "tester", password = "Password1!", couponId = coupon.id),
        ).id
    }

    private fun command(quantity: Int, couponId: Long?) = OrderCommand(
        loginId = "tester",
        password = "Password1!",
        items = listOf(OrderCommand.OrderItemCommand(productId = productId, quantity = quantity)),
        couponId = couponId,
    )
}
```

- [ ] **Step 6: 통과 확인 및 커밋**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.*"`
Expected: PASS

```bash
git add -A
git commit -m "feat: apply coupon to order in single transaction"
```

---

### Task 13: 쿠폰 사용 / 중복 발급 동시성 테스트

**Files:**
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/application/coupon/CouponConcurrencyTest.kt`

`@Version`(Task 5)과 유니크 제약 + saveAndFlush(Task 6~7)가 이미 구현돼 있으므로 이 테스트는 통과가 기대된다. 실패하면 그 지점이 버그다 — 테스트를 약화하지 말고 구현을 고친다.

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.loopers.application.coupon

import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class CouponConcurrencyTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val issueCouponUsecase: IssueCouponUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    fun usesCouponExactlyOnce_whenOrderedConcurrently() {
        // arrange
        val threadCount = 5
        val user = signUp("buyer")
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = 100))
        val coupon = saveCoupon()
        val userCouponId = issueCouponUsecase.execute(
            IssueCouponCommand(loginId = "buyer", password = PASSWORD, couponId = coupon.id),
        ).id

        // act: 같은 사용자가 같은 쿠폰으로 동시 주문 (여러 기기 시나리오)
        val errors = runConcurrently(threadCount) {
            createOrderUsecase.execute(
                OrderCommand(
                    loginId = "buyer",
                    password = PASSWORD,
                    items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = 1)),
                    couponId = userCouponId,
                ),
            )
        }

        // assert: 정확히 1건 성공, 실패 주문의 재고 차감은 롤백
        val successCount = threadCount - errors.size
        assertAll(
            { assertThat(successCount).isEqualTo(1) },
            { assertThat(userCouponRepository.findByIdAndUserId(userCouponId, user.id)!!.status).isEqualTo(UserCouponStatus.USED) },
            { assertThat(productStockRepository.findByProductId(product.id)!!.quantity).isEqualTo(99) },
        )
    }

    @DisplayName("동일 사용자가 같은 쿠폰을 동시에 발급 요청해도 한 장만 발급된다.")
    @Test
    fun issuesCouponExactlyOnce_whenRequestedConcurrently() {
        // arrange
        val threadCount = 5
        val user = signUp("issuer")
        val coupon = saveCoupon()

        // act
        val errors = runConcurrently(threadCount) {
            issueCouponUsecase.execute(IssueCouponCommand(loginId = "issuer", password = PASSWORD, couponId = coupon.id))
        }

        // assert
        assertAll(
            { assertThat(threadCount - errors.size).isEqualTo(1) },
            { assertThat(userCouponRepository.findAllByUserId(user.id)).hasSize(1) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    private fun saveCoupon() = couponRepository.save(
        CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal("1000"),
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )
}
```

- [ ] **Step 2: 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.coupon.CouponConcurrencyTest"`
Expected: PASS — 실패 시 구현(낙관적 락, 유니크 제약 변환)을 고친다. 흔한 원인: ① 쿠폰 사용 경합의 실패가 `OptimisticLockingFailureException`이 아닌 다른 예외로 새는 경우, ② 발급의 `DataIntegrityViolationException`이 saveAndFlush 이전에 발생하지 않는 경우.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m "test: verify coupon single-use and single-issue under concurrency"
```

---

### Task 14: .http 실행 예시 + 전체 검증

**Files:**
- Create: `http/commerce-api/coupon-v1.http`, `http/commerce-api/order-v1.http`

- [ ] **Step 1: .http 파일 작성**

`http/commerce-api/coupon-v1.http`:

```http
### [ADMIN] 쿠폰 템플릿 등록 (정률)
POST http://localhost:8080/api-admin/v1/coupons
Content-Type: application/json
X-Loopers-Ldap: admin.user

{
  "name": "신규가입 10% 할인",
  "type": "RATE",
  "value": 10,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59"
}

### [ADMIN] 쿠폰 템플릿 등록 (정액)
POST http://localhost:8080/api-admin/v1/coupons
Content-Type: application/json
X-Loopers-Ldap: admin.user

{
  "name": "3000원 할인",
  "type": "FIXED",
  "value": 3000,
  "minOrderAmount": null,
  "expiredAt": "2026-12-31T23:59:59"
}

### [ADMIN] 쿠폰 템플릿 목록 조회
GET http://localhost:8080/api-admin/v1/coupons?page=0&size=20
X-Loopers-Ldap: admin.user

### [ADMIN] 쿠폰 템플릿 상세 조회
GET http://localhost:8080/api-admin/v1/coupons/1
X-Loopers-Ldap: admin.user

### [ADMIN] 쿠폰 템플릿 수정
PUT http://localhost:8080/api-admin/v1/coupons/1
Content-Type: application/json
X-Loopers-Ldap: admin.user

{
  "name": "신규가입 15% 할인",
  "type": "RATE",
  "value": 15,
  "minOrderAmount": 10000,
  "expiredAt": "2026-12-31T23:59:59"
}

### [ADMIN] 쿠폰 템플릿 삭제
DELETE http://localhost:8080/api-admin/v1/coupons/1
X-Loopers-Ldap: admin.user

### [ADMIN] 쿠폰 발급 내역 조회
GET http://localhost:8080/api-admin/v1/coupons/1/issues?page=0&size=20
X-Loopers-Ldap: admin.user

### 쿠폰 발급
POST http://localhost:8080/api/v1/coupons/1/issue
X-Loopers-LoginId: tester
X-Loopers-LoginPw: Password1!

### 내 쿠폰 목록 조회
GET http://localhost:8080/api/v1/users/me/coupons
X-Loopers-LoginId: tester
X-Loopers-LoginPw: Password1!
```

`http/commerce-api/order-v1.http`:

```http
### 주문 생성 (쿠폰 미적용)
POST http://localhost:8080/api/v1/orders
Content-Type: application/json
X-Loopers-LoginId: tester
X-Loopers-LoginPw: Password1!

{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}

### 주문 생성 (쿠폰 적용)
POST http://localhost:8080/api/v1/orders
Content-Type: application/json
X-Loopers-LoginId: tester
X-Loopers-LoginPw: Password1!

{
  "items": [
    { "productId": 1, "quantity": 2 }
  ],
  "couponId": 1
}
```

- [ ] **Step 2: 전체 테스트 + 린트**

Run: `./gradlew :apps:commerce-api:test :apps:commerce-api:ktlintCheck`
Expected: BUILD SUCCESSFUL — 실패하는 테스트 0건

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m "docs: add coupon and order http examples"
```

---

## Self-Review 결과 (작성 시 반영 완료)

- 스펙의 모든 요구사항에 대응 태스크 존재: 쿠폰 도메인(4~6), 대고객 API(7~8), 어드민(9), 주문 통합(10~12), 좋아요 수정(1~2), syncStock(3), 동시성 테스트 4종(1, 11, 13), .http(14)
- 스펙과 다른 결정 1건: 낙관적 락 예외 변환은 usecase try-catch가 아니라 `ApiControllerAdvice` — `@Version` 충돌이 커밋 시점(usecase 반환 후)에 발생하기 때문 (Task 12 Step 3에 사유 명시)
- 타입 일관성: `discountValue`(도메인) ↔ `value`(API JSON, 과제 명세 준수) 변환은 DTO에서만 수행
```
