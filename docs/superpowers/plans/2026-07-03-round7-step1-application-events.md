# Round 7 Step 1 — ApplicationEvent 경계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문/조회/좋아요/결제 흐름의 부가 로직(유저행동 로깅, 알림)을 `ApplicationEvent`로 분리해, 핵심 트랜잭션과 부가 로직의 경계를 이벤트로 표현한다.

**Architecture:** 각 usecase는 커밋 시점에 도메인 이벤트를 발행한다(`ApplicationEventPublisher`). 집계(`ProductLikeCountEventHandler`)는 기존대로 동기 `AFTER_COMMIT`; 신규 로깅/알림 핸들러는 `@Async` + `AFTER_COMMIT`로 응답 경로와 분리한다. 이벤트는 순수 데이터 클래스, 핸들러 로직은 순수 함수(`describe`/`message`)로 뽑아 직접 호출 단위 테스트한다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, JUnit5 + AssertJ, Spring `@TransactionalEventListener`/`@Async`. mockk/mockito 미사용 — 기존 관례대로 손수 만든 in-memory fake 사용.

## Global Constraints

- 레이어 의존 방향: `interfaces → application → domain`. 이벤트 정의는 `domain/{x}`, 핸들러는 `application/{x}`.
- 도메인 이벤트는 프레임워크/DTO에 의존하지 않는 순수 data class.
- 기존 `ProductLikeCountEventHandler`(동기 AFTER_COMMIT, REQUIRES_NEW)는 **변경하지 않는다**(집계 정합성 유지).
- 테스트는 3A, 기존 fake 패턴 재사용(`RecordingEventPublisher`, in-memory repository). 새 의존성 추가 금지.
- ktlint 준수(최대 줄길이 130, 다중 인자 각 줄 + trailing comma). 커밋 전 `./gradlew :apps:commerce-api:ktlintCheck` 통과.
- 커밋 메시지 말미에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: ProductViewedEvent 발행 (상품 상세 조회)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductEvent.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/product/usecase/GetProductDetailUsecase.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/product/usecase/GetProductDetailUsecaseEventTest.kt`

**Interfaces:**
- Produces: `data class ProductViewedEvent(val productId: Long)` — Task 4 `UserActionLogEventHandler`가 소비.
- 발행 지점: `GetProductDetailUsecase.execute(productId)` 최상단. `execute`가 `@Transactional(readOnly = true)`이므로 성공 시 커밋되어 AFTER_COMMIT 리스너 발화; 상품 없음(NOT_FOUND) 예외 시 롤백되어 발화 안 됨 → "성공한 조회만 카운트"가 자연히 성립.

- [ ] **Step 1: 실패 테스트 작성**

`GetProductDetailUsecaseEventTest.kt` (in-memory fake는 `ProductLikeCountEventHandlerTest.kt`의 `InMemoryProductRepository`/`RecordingProductCacheRepository` 및 `LikeProductUsecaseTest.kt`의 `RecordingEventPublisher` 패턴을 그대로 복제):

```kotlin
package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal

class GetProductDetailUsecaseEventTest {
    @DisplayName("상품 상세 조회에 성공하면 ProductViewedEvent 를 발행한다.")
    @Test
    fun publishesProductViewedEvent_onSuccess() {
        // arrange
        val fixture = Fixture()

        // act
        fixture.usecase.execute(10L)

        // assert
        assertThat(fixture.eventPublisher.events).containsExactly(ProductViewedEvent(productId = 10L))
    }

    private class Fixture {
        val eventPublisher = RecordingEventPublisher()
        val usecase = GetProductDetailUsecase(
            productRepository = InMemoryProductRepository(),
            brandRepository = InMemoryBrandRepository(),
            productStockRepository = InMemoryProductStockRepository(),
            productCacheRepository = NoopProductCacheRepository(),
            eventPublisher = eventPublisher,
        )
    }

    private class RecordingEventPublisher : ApplicationEventPublisher {
        val events = mutableListOf<Any>()
        override fun publishEvent(event: Any) { events.add(event) }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val product = ProductModel(
            brandId = 1L, name = "Air Max", description = "Shoes", price = BigDecimal("120000.00"),
        ).withId(10L)
        override fun save(product: ProductModel) = product
        override fun findActiveById(id: Long) = product.takeIf { id == 10L }
        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> =
            PageImpl(listOf(product), pageable, 1)
        override fun existsActiveById(id: Long) = id == 10L
        override fun incrementLikeCount(productId: Long) = Unit
        override fun decrementLikeCount(productId: Long) = Unit
    }

    private class InMemoryBrandRepository : BrandRepository {
        override fun findActiveById(id: Long) = BrandModel(name = "Nike").withId(1L)
    }

    private class InMemoryProductStockRepository : ProductStockRepository {
        override fun findByProductId(productId: Long) = ProductStockModel(productId = productId, quantity = 10)
        override fun findByProductIdForUpdate(productId: Long) = findByProductId(productId)
        override fun save(stock: ProductStockModel) = stock
    }

    private class NoopProductCacheRepository : ProductCacheRepository {
        override fun getDetail(productId: Long): ProductInfo? = null
        override fun putDetail(productId: Long, product: ProductInfo) = Unit
        override fun evictDetail(productId: Long) = Unit
        override fun getList(query: ProductCacheRepository.ProductListCacheQuery): ProductPageInfo? = null
        override fun putList(query: ProductCacheRepository.ProductListCacheQuery, products: ProductPageInfo) = Unit
    }
}
```

> 참고: `BrandRepository`/`ProductStockRepository`/`ProductCacheRepository`의 실제 인터페이스 메서드 시그니처는 각 도메인 파일에서 확인해 fake를 맞춘다(위는 탐색된 시그니처 기준; 컴파일 에러 나면 실제 포트에 맞춰 조정). `BrandModel` 생성자 인자도 실제 정의에 맞춘다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.usecase.GetProductDetailUsecaseEventTest"`
Expected: FAIL — `ProductViewedEvent`/`ProductEvent` 심볼 미해결 컴파일 에러, 또는 `GetProductDetailUsecase` 생성자에 `eventPublisher` 없음.

- [ ] **Step 3: 이벤트 정의 + 발행 구현**

`domain/product/ProductEvent.kt` (기존 `domain/like/LikeEvent.kt` 컨벤션):
```kotlin
package com.loopers.domain.product

data class ProductViewedEvent(
    val productId: Long,
)
```

`GetProductDetailUsecase.kt` 수정 — 생성자에 publisher 추가, `execute` 최상단에서 발행:
```kotlin
import org.springframework.context.ApplicationEventPublisher
import com.loopers.domain.product.ProductViewedEvent
// ...
@Component
class GetProductDetailUsecase(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockRepository: ProductStockRepository,
    private val productCacheRepository: ProductCacheRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // ...
    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductInfo {
        eventPublisher.publishEvent(ProductViewedEvent(productId = productId))
        runCatching { productCacheRepository.getDetail(productId) }
        // ... (나머지 기존 코드 그대로)
```

> `ProductViewedEvent` import는 같은 패키지가 아니면 추가. `ProductEvent.kt`가 `domain.product`이므로 usecase에서 import 필요.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.product.usecase.GetProductDetailUsecaseEventTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductEvent.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/product/usecase/GetProductDetailUsecase.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/product/usecase/GetProductDetailUsecaseEventTest.kt
git commit -m "feat: publish ProductViewedEvent on product detail view (R7-A)"
```

---

### Task 2: OrderCreatedEvent 발행 (주문 생성)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderEvent.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/usecase/CreateOrderUsecase.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/usecase/CreateOrderUsecaseTest.kt` (기존 파일에 테스트 추가 + fixture에 publisher 주입)

**Interfaces:**
- Produces: `data class OrderCreatedEvent(val orderId: Long, val userId: Long, val items: List<Item>)`, `data class OrderCreatedEvent.Item(val productId: Long, val quantity: Int)` — Task 4/5 핸들러가 소비.
- Consumes: 없음.
- 발행 지점: `CreateOrderUsecase.execute`에서 `orderRepository.save(...)` 직후, 저장된 `OrderModel`로 이벤트 구성 후 발행. 반환은 기존과 동일 `OrderInfo`.

- [ ] **Step 1: 실패 테스트 작성**

`CreateOrderUsecaseTest.kt`의 `Fixture`에 `RecordingEventPublisher`를 추가하고(`LikeProductUsecaseTest`와 동일 구현), `CreateOrderUsecase(... eventPublisher = eventPublisher)`로 주입. 새 테스트 추가:
```kotlin
@DisplayName("주문을 저장한 뒤 OrderCreatedEvent 를 발행한다.")
@Test
fun publishesOrderCreatedEvent() {
    // arrange
    val fixture = Fixture()

    // act
    val order = fixture.createOrderUsecase.execute(fixture.command())

    // assert
    assertThat(fixture.eventPublisher.events).containsExactly(
        OrderCreatedEvent(
            orderId = order.id,
            userId = order.userId,
            items = order.items.map { OrderCreatedEvent.Item(productId = it.productId, quantity = it.quantity) },
        ),
    )
}
```
import 추가: `import com.loopers.domain.order.OrderCreatedEvent`, `import org.springframework.context.ApplicationEventPublisher`.
`Fixture`에 추가:
```kotlin
val eventPublisher = RecordingEventPublisher()
// createOrderUsecase 생성자에 eventPublisher = eventPublisher 추가
// 그리고 파일 하단에:
private class RecordingEventPublisher : ApplicationEventPublisher {
    val events = mutableListOf<Any>()
    override fun publishEvent(event: Any) { events.add(event) }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.usecase.CreateOrderUsecaseTest"`
Expected: FAIL — `OrderCreatedEvent` 미해결 / `CreateOrderUsecase` 생성자에 `eventPublisher` 없음.

- [ ] **Step 3: 이벤트 정의 + 발행 구현**

`domain/order/OrderEvent.kt`:
```kotlin
package com.loopers.domain.order

data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
```

`CreateOrderUsecase.kt` 수정 — 생성자에 publisher 추가, save 결과 캡처 후 발행:
```kotlin
import org.springframework.context.ApplicationEventPublisher
import com.loopers.domain.order.OrderCreatedEvent
// ...
class CreateOrderUsecase(
    // ... 기존 6개 의존성 ...
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // ...
    @Transactional
    fun execute(command: OrderCommand): OrderInfo {
        // ... user/couponApplication/orderProducts 기존 그대로 ...
        val saved = orderDomainService.create(
            userId = user.id,
            items = orderProducts,
            couponApplication = couponApplication,
            now = ZonedDateTime.now(),
        ).let { orderRepository.save(it) }

        eventPublisher.publishEvent(
            OrderCreatedEvent(
                orderId = saved.id,
                userId = saved.userId,
                items = saved.items.map { OrderCreatedEvent.Item(productId = it.productId, quantity = it.quantity) },
            ),
        )
        return OrderInfo.from(saved)
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.usecase.CreateOrderUsecaseTest"`
Expected: PASS (기존 테스트 + 신규 테스트 모두).

- [ ] **Step 5: 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderEvent.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/order/usecase/CreateOrderUsecase.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/order/usecase/CreateOrderUsecaseTest.kt
git commit -m "feat: publish OrderCreatedEvent after order save (R7-A)"
```

---

### Task 3: PaymentSucceededEvent 발행 (결제 성공)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEvent.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/usecase/SyncPaymentResultUsecase.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/SyncPaymentResultUsecaseIntegrationTest.kt` (기존 @SpringBootTest에 `@RecordApplicationEvents`로 발행 검증 추가)

**Interfaces:**
- Produces: `data class PaymentSucceededEvent(val orderId: Long, val userId: Long, val items: List<Item>)`, `data class PaymentSucceededEvent.Item(val productId: Long, val quantity: Int)` — Task 4/5 핸들러가 소비. (Step B에서 order-events 판매량 집계 소스)
- 발행 지점: `SyncPaymentResultUsecase.apply`의 `command.status == PgStatus.SUCCESS` 정상 분기에서 `compareAndSetStatus(... SUCCESS ...)` affected==1 이고 `order.markAsPaid()` 성공 직후. (REFUND_REQUIRED/FAILED 분기에서는 발행하지 않음)

- [ ] **Step 1: 실패 테스트 작성**

기존 `SyncPaymentResultUsecaseIntegrationTest`는 @SpringBootTest. 클래스에 `@org.springframework.test.context.event.RecordApplicationEvents` 추가하고, 결제 성공 시나리오 테스트에 다음 검증을 추가(또는 신규 테스트 메서드):
```kotlin
// 클래스 레벨
@RecordApplicationEvents
@SpringBootTest
class SyncPaymentResultUsecaseIntegrationTest {
    @Autowired lateinit var applicationEvents: org.springframework.test.context.event.ApplicationEvents
    // ...

    @DisplayName("결제가 성공하면 PaymentSucceededEvent 를 발행한다.")
    @Test
    fun publishesPaymentSucceededEvent_onSuccess() {
        // arrange: PENDING 주문 + PENDING 결제 저장 (기존 성공 시나리오 setup 재사용)
        // ... 기존 성공 테스트의 arrange 그대로 ...

        // act
        syncPaymentResultUsecase.apply(successCommand)

        // assert
        val published = applicationEvents.stream(PaymentSucceededEvent::class.java).toList()
        assertThat(published).hasSize(1)
        assertThat(published.first().orderId).isEqualTo(order.id)
    }
}
```
import: `import com.loopers.domain.payment.PaymentSucceededEvent`, `import org.springframework.test.context.event.ApplicationEvents`, `import org.springframework.test.context.event.RecordApplicationEvents`.

> 기존 성공 테스트가 있으면 그 arrange/act를 재사용해 assert만 추가. `@RecordApplicationEvents`는 각 테스트의 발행 이벤트를 캡처한다(mockk 불필요).

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.SyncPaymentResultUsecaseIntegrationTest"`
Expected: FAIL — `PaymentSucceededEvent` 미해결, 또는 published 비어 있음(0 != 1).

- [ ] **Step 3: 이벤트 정의 + 발행 구현**

`domain/payment/PaymentEvent.kt`:
```kotlin
package com.loopers.domain.payment

data class PaymentSucceededEvent(
    val orderId: Long,
    val userId: Long,
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
```

`SyncPaymentResultUsecase.kt` 수정 — 생성자에 `private val eventPublisher: ApplicationEventPublisher` 추가. SUCCESS 정상 분기(현재 line 77-85):
```kotlin
import org.springframework.context.ApplicationEventPublisher
import com.loopers.domain.payment.PaymentSucceededEvent
// ...
command.status == PgStatus.SUCCESS -> {
    val affected = paymentRepository.compareAndSetStatus(payment.id, PaymentStatus.SUCCESS, null, now)
    if (affected == 1) {
        val order = orderRepository.findById(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
        order.markAsPaid()
        eventPublisher.publishEvent(
            PaymentSucceededEvent(
                orderId = order.id,
                userId = order.userId,
                items = order.items.map {
                    PaymentSucceededEvent.Item(productId = it.productId, quantity = it.quantity)
                },
            ),
        )
    }
}
```

> `apply`가 `@Transactional`이므로, 발행 이벤트를 소비하는 AFTER_COMMIT 핸들러는 이 트랜잭션 커밋 후 발화. `@RecordApplicationEvents`는 커밋과 무관하게 발행 자체를 기록하므로 테스트는 통과.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.SyncPaymentResultUsecaseIntegrationTest"`
Expected: PASS (기존 전 케이스 + 신규).

- [ ] **Step 5: 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEvent.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/payment/usecase/SyncPaymentResultUsecase.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/payment/SyncPaymentResultUsecaseIntegrationTest.kt
git commit -m "feat: publish PaymentSucceededEvent on payment success (R7-A)"
```

---

### Task 4: AsyncConfig + UserActionLogEventHandler (유저행동 로깅)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/config/AsyncConfig.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/log/UserActionLogEventHandler.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/log/UserActionLogEventHandlerTest.kt`

**Interfaces:**
- Consumes: `ProductViewedEvent`(Task 1), `LikeCreatedEvent`/`LikeDeletedEvent`(기존 `domain.like`), `OrderCreatedEvent`(Task 2), `PaymentSucceededEvent`(Task 3).
- Produces: `UserActionLogEventHandler.describe(event): String` (순수 함수, 각 이벤트 → 구조화 로그 문자열) — 단위 테스트 대상.
- `@Async` 실행을 위해 `AsyncConfig`(@EnableAsync)가 필요. 두 파일을 한 태스크로 묶는다.

- [ ] **Step 1: 실패 테스트 작성**

`UserActionLogEventHandlerTest.kt` — 순수 함수 `describe` 검증(핸들러 로직의 결정적 단위 테스트, 로그 부작용은 직접 호출로 예외 없음만 확인):
```kotlin
package com.loopers.application.log

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.product.ProductViewedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserActionLogEventHandlerTest {
    private val handler = UserActionLogEventHandler()

    @DisplayName("상품 조회 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesProductViewed() {
        assertThat(handler.describe(ProductViewedEvent(productId = 10L)))
            .isEqualTo("USER_ACTION type=VIEW productId=10")
    }

    @DisplayName("좋아요 생성 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesLikeCreated() {
        assertThat(handler.describe(LikeCreatedEvent(productId = 10L)))
            .isEqualTo("USER_ACTION type=LIKE productId=10")
    }

    @DisplayName("주문 생성 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesOrderCreated() {
        val event = OrderCreatedEvent(
            orderId = 1L, userId = 2L, items = listOf(OrderCreatedEvent.Item(productId = 10L, quantity = 3)),
        )
        assertThat(handler.describe(event))
            .isEqualTo("USER_ACTION type=ORDER userId=2 orderId=1 items=1")
    }

    @DisplayName("결제 성공 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesPaymentSucceeded() {
        val event = PaymentSucceededEvent(
            orderId = 1L, userId = 2L, items = listOf(PaymentSucceededEvent.Item(productId = 10L, quantity = 3)),
        )
        assertThat(handler.describe(event))
            .isEqualTo("USER_ACTION type=PAYMENT userId=2 orderId=1 items=1")
    }

    @DisplayName("핸들러 호출은 예외 없이 완료된다(스모크).")
    @Test
    fun handleDoesNotThrow() {
        handler.handle(ProductViewedEvent(productId = 10L))
        handler.handle(LikeCreatedEvent(productId = 10L))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.log.UserActionLogEventHandlerTest"`
Expected: FAIL — `UserActionLogEventHandler` 미해결.

- [ ] **Step 3: AsyncConfig + 핸들러 구현**

`config/AsyncConfig.kt`:
```kotlin
package com.loopers.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    private val log = LoggerFactory.getLogger(AsyncConfig::class.java)

    @Bean("applicationEventExecutor")
    fun applicationEventExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 100
            setThreadNamePrefix("app-event-")
            initialize()
        }

    override fun getAsyncExecutor(): java.util.concurrent.Executor = applicationEventExecutor()

    // 예외 은닉 방지: @Async 리스너에서 삼켜진 예외를 로깅한다.
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler =
        AsyncUncaughtExceptionHandler { ex, method, _ ->
            log.error("Async event handler failed. method={}", method.name, ex)
        }
}
```

`application/log/UserActionLogEventHandler.kt`:
```kotlin
package com.loopers.application.log

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.product.ProductViewedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserActionLogEventHandler {
    private val log = LoggerFactory.getLogger(UserActionLogEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: ProductViewedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeCreatedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeDeletedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderCreatedEvent) = log.info(describe(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentSucceededEvent) = log.info(describe(event))

    fun describe(event: ProductViewedEvent) = "USER_ACTION type=VIEW productId=${event.productId}"
    fun describe(event: LikeCreatedEvent) = "USER_ACTION type=LIKE productId=${event.productId}"
    fun describe(event: LikeDeletedEvent) = "USER_ACTION type=UNLIKE productId=${event.productId}"
    fun describe(event: OrderCreatedEvent) =
        "USER_ACTION type=ORDER userId=${event.userId} orderId=${event.orderId} items=${event.items.size}"
    fun describe(event: PaymentSucceededEvent) =
        "USER_ACTION type=PAYMENT userId=${event.userId} orderId=${event.orderId} items=${event.items.size}"
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.log.UserActionLogEventHandlerTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/config/AsyncConfig.kt \
        apps/commerce-api/src/main/kotlin/com/loopers/application/log/UserActionLogEventHandler.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/log/UserActionLogEventHandlerTest.kt
git commit -m "feat: async user-action logging via ApplicationEvent (R7-A)"
```

---

### Task 5: NotificationEventHandler (알림 스텁)

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/notification/NotificationEventHandler.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/notification/NotificationEventHandlerTest.kt`

**Interfaces:**
- Consumes: `OrderCreatedEvent`(주문 접수 알림), `PaymentSucceededEvent`(결제 완료 → 주문 완료 알림).
- Produces: `NotificationEventHandler.message(event): String` (순수 함수) — 단위 테스트 대상.
- 실제 전송 없음(스텁). `@Async` + `AFTER_COMMIT`. 주 로직(주문/결제 커밋)과 부가 로직(알림)의 경계를 이벤트로 시연.

- [ ] **Step 1: 실패 테스트 작성**

`NotificationEventHandlerTest.kt`:
```kotlin
package com.loopers.application.notification

import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NotificationEventHandlerTest {
    private val handler = NotificationEventHandler()

    @DisplayName("주문 생성 이벤트를 주문 접수 알림 메시지로 변환한다.")
    @Test
    fun messageForOrderCreated() {
        val event = OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList())
        assertThat(handler.message(event))
            .isEqualTo("NOTIFY user=2 주문(1)이 접수되었습니다.")
    }

    @DisplayName("결제 성공 이벤트를 주문 완료 알림 메시지로 변환한다.")
    @Test
    fun messageForPaymentSucceeded() {
        val event = PaymentSucceededEvent(orderId = 1L, userId = 2L, items = emptyList())
        assertThat(handler.message(event))
            .isEqualTo("NOTIFY user=2 주문(1) 결제가 완료되었습니다.")
    }

    @DisplayName("핸들러 호출은 예외 없이 완료된다(스모크).")
    @Test
    fun handleDoesNotThrow() {
        handler.handle(OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList()))
        handler.handle(PaymentSucceededEvent(orderId = 1L, userId = 2L, items = emptyList()))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.notification.NotificationEventHandlerTest"`
Expected: FAIL — `NotificationEventHandler` 미해결.

- [ ] **Step 3: 핸들러 구현**

`application/notification/NotificationEventHandler.kt`:
```kotlin
package com.loopers.application.notification

import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 알림 스텁: 실제 전송 없이 "발송했다"는 사실만 로깅한다.
// 주 로직(주문/결제)과 부가 로직(알림)의 경계를 ApplicationEvent 로 분리한 예시.
@Component
class NotificationEventHandler {
    private val log = LoggerFactory.getLogger(NotificationEventHandler::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderCreatedEvent) = log.info(message(event))

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: PaymentSucceededEvent) = log.info(message(event))

    fun message(event: OrderCreatedEvent) = "NOTIFY user=${event.userId} 주문(${event.orderId})이 접수되었습니다."
    fun message(event: PaymentSucceededEvent) = "NOTIFY user=${event.userId} 주문(${event.orderId}) 결제가 완료되었습니다."
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :apps:commerce-api:test --tests "com.loopers.application.notification.NotificationEventHandlerTest"`
Expected: PASS.

- [ ] **Step 5: 전체 회귀 + 커밋**

```bash
./gradlew :apps:commerce-api:test -q
./gradlew :apps:commerce-api:ktlintCheck -q
git add apps/commerce-api/src/main/kotlin/com/loopers/application/notification/NotificationEventHandler.kt \
        apps/commerce-api/src/test/kotlin/com/loopers/application/notification/NotificationEventHandlerTest.kt
git commit -m "feat: notification stub handler via ApplicationEvent (R7-A)"
```

---

## Step 1 완료 기준 (Definition of Done)

- 조회/좋아요/주문/결제성공 이벤트가 각 usecase에서 발행된다(Task 1-3, 단위/통합 테스트로 증명).
- 유저행동 로깅·알림이 `@Async` + `AFTER_COMMIT` 핸들러로 분리된다(Task 4-5).
- 집계(`ProductLikeCountEventHandler`)는 동기 유지 — 변경 없음. 집계 실패와 무관하게 좋아요/조회는 성공.
- `./gradlew :apps:commerce-api:test`, `:apps:commerce-api:ktlintCheck` 전부 통과.

## Self-Review (spec 대비)

- **spec §6 유저행동 이벤트(조회/좋아요/주문)** → Task 1(조회), 기존 like(좋아요), Task 2(주문). ✅
- **spec §6 좋아요→집계 유지** → 변경 없음 명시. ✅
- **spec §6 알림 스텁** → Task 5. ✅
- **spec §6 유저행동 로깅** → Task 4. ✅
- **spec §6 @Async 정책(로깅·알림만 비동기, 집계 동기)** → Task 4/5 @Async, 집계 무변경. ✅
- **spec 리스너 phase 매핑(집계 AFTER_COMMIT/REQUIRES_NEW, 로깅·알림 AFTER_COMMIT/@Async)** → 준수. ✅
- 미커버(의도적, Step B/C): Outbox·Kafka·product_metrics·선착순. 본 플랜은 Step 1(내부 이벤트)만.
- 타입 일관성: `OrderCreatedEvent.Item`/`PaymentSucceededEvent.Item` 필드(productId, quantity)가 Task 2/3 정의와 Task 4/5 소비에서 일치. `describe`/`message` 시그니처가 테스트와 구현에서 일치.

## 다음 단계

Step 1 구현·검증 완료 후, 같은 방식으로 **Plan B (Step 2 — Kafka + Outbox)** 를 작성한다.
그때 `analyze-query`/`analyze-external-integration` 스킬로 이벤트/트랜잭션 경계를 점검한다.
