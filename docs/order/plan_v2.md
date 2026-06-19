# Order Payment Stock V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docs/order/design_v2.md`의 주문/결제/재고 예약 정합성 설계를 현재 Kotlin Spring Boot 코드에 반영한다.

**Architecture:** 결제 projection은 `domain.payment`의 `Payment`로, 감사 로그는 append-only `PaymentEvent`로 분리한다. PG approve/verify/cancel 호출은 facade에서 트랜잭션 밖으로 빼고, 호출 전후의 DB 변경은 작은 application service 트랜잭션으로 나눈다. 재고 예약/확정/반환은 `product_stocks.reserved_quantity`와 조건부 atomic update의 affected row 확인으로 처리한다.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, Spring Data JPA, MySQL/Testcontainers, JUnit 5, Spring Batch.

---

## Scope Check

설계 v2는 주문, 결제, 재고, 카탈로그 조회, 배치까지 걸쳐 있다. 하나의 상태 모델과 같은 테이블을 공유해야 하므로 이 문서는 하나의 실행 계획으로 둔다. 각 Task는 독립적으로 테스트 가능한 단위이며, Task마다 커밋한다.

API endpoint와 request/response DTO의 상세 설계는 `design_v2.md`의 제외 범위다. 다만 `OrderCommand.Pay`에 `paymentKey`가 필요하고 주문 응답에서 기존 `paymentTransactionId` 소유권이 바뀌므로 컴파일과 기존 API 테스트 보정을 위한 최소 수정은 포함한다.

`apps/commerce-batch`는 현재 `apps/commerce-api`의 application/domain 클래스를 의존할 수 없다. 배치 Task는 앱 간 의존을 만들지 않고, 같은 DB 스키마를 대상으로 `NamedParameterJdbcTemplate` 기반 worker를 둔다. API의 핵심 상태 전이 구현을 먼저 완성한 뒤 배치 worker는 동일한 SQL 조건과 이벤트 타입을 사용한다.

## File Structure

### Create

- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PgProvider.kt`: PG namespace enum.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentStatus.kt`: payment projection 상태 enum.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEventType.kt`: append-only event 타입 enum.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/Payment.kt`: `payments` JPA entity와 상태 전이 메서드.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEvent.kt`: `payment_events` JPA entity.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentRepository.kt`: payment domain repository interface.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEventRepository.kt`: payment event repository interface.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentCommand.kt`: approve/verify/cancel command와 주석.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentGateway.kt`: 결제 영역 PG port.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentInfo.kt`: facade와 controller가 쓰는 결제 projection info.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt`: payment 상태 갱신과 event append를 같은 트랜잭션에서 처리.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt`: 승인된 결제의 내부 완료와 실패 마킹 트랜잭션.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentJpaRepository.kt`: Spring Data JPA repository.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentRepositoryImpl.kt`: domain repository 구현.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentEventJpaRepository.kt`: Spring Data JPA repository.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentEventRepositoryImpl.kt`: event repository 구현.
- `apps/commerce-api/src/test/kotlin/com/loopers/domain/payment/PaymentTest.kt`: payment 상태 전이 단위 테스트.
- `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentApplicationServiceTest.kt`: 상태 갱신과 event append 통합 테스트.
- `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImplIntegrationTest.kt`: atomic stock update 통합 테스트.
- `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/OrderReservationExpirationJobConfig.kt`: 예약 만료 배치 Job.
- `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/step/OrderReservationExpirationTasklet.kt`: 만료 worker.
- `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/PaymentCompletionRetryJobConfig.kt`: 내부 완료 실패 재시도 배치 Job.
- `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/step/PaymentCompletionRetryTasklet.kt`: retry worker.
- `apps/commerce-batch/src/test/kotlin/com/loopers/job/order/OrderReservationExpirationJobE2ETest.kt`: 만료 배치 E2E.
- `apps/commerce-batch/src/test/kotlin/com/loopers/job/order/PaymentCompletionRetryJobE2ETest.kt`: retry 배치 E2E.

### Modify

- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt`: `FAILED`, `EXPIRED` 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt`: `paymentTransactionId` 제거, v2 상태 전이 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderRepository.kt`: v2 조건부 상태 update 메서드로 교체.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderJpaRepository.kt`: `PAYMENT_PENDING/FAILED/COMPLETED` 전이 쿼리 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderRepositoryImpl.kt`: 새 repository interface 구현.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderApplicationService.kt`: 완료, 실패, 만료, 취소 전이 메서드 정리.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt`: payment transaction을 order entity에서 읽지 않도록 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderCommand.kt`: `Pay(paymentKey)` 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservationStatus.kt`: `IN_PROGRESS`, `EXPIRED`, `COMPLETED`, `CANCELED`로 교체.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservation.kt`: v2 reservation 전이 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservationRepository.kt`: `transitionByOrderId`와 상태별 조회 메서드 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationJpaRepository.kt`: 조건부 update 쿼리 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationRepositoryImpl.kt`: `OrderReservationQueryPort` 의존 제거 또는 reserved quantity 기반으로 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/StockApplicationService.kt`: reserve/confirm/expire/cancel/restore를 atomic stock update 기반으로 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStock.kt`: `reservedQuantity` 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStockRepository.kt`: atomic update 메서드 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockJpaRepository.kt`: `reserveIfAvailable`, `confirmReserved`, `releaseReserved`, `restoreActualStock` 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImpl.kt`: affected row boolean 반환.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/CatalogStockPort.kt`: lock 기반 API를 atomic update API로 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CatalogOrderStockAdapter.kt`: port 구현 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CatalogInfo.kt`: display row에 `reservedQuantity` 또는 `availableQuantity` 추가.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/CatalogProductQueryDao.kt`: `stock.reservedQuantity` 조회.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/ProductQueryFacade.kt`: soldOut 계산을 `availableQuantity <= 0`로 통일.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CartCatalogAdapter.kt`: cart orderable을 available quantity 기준으로 계산.
- `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`: PG 호출 트랜잭션 분리, approve/verify/cancel orchestration 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/FakePaymentGateway.kt`: `application.payment.PaymentGateway` 구현으로 변경.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Dto.kt`: `PayRequest(paymentKey)` 최소 추가, transaction id 소유권 보정.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Controller.kt`: payment endpoint가 request body를 받도록 변경.
- Existing tests under `apps/commerce-api/src/test/kotlin/com/loopers/application/order`, `domain/order`, `interfaces/api`: v2 status와 payment event expectation으로 갱신.

---

## Task 1: Order And Reservation Domain States

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderCommand.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservationStatus.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservation.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/StockReservationTest.kt`

- [ ] **Step 1: Write failing order state tests**

Replace the `Transition` nested class in `OrderTest.kt` with tests that assert v2 states:

```kotlin
@Nested
inner class Transition {
    @Test
    fun completeChangesPaymentPendingToCompletedWithoutPaymentTransactionOnOrder() {
        val order = pendingOrder()

        order.complete()

        assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
    }

    @Test
    fun markCompletionFailedChangesPaymentPendingToFailed() {
        val order = pendingOrder()

        order.markCompletionFailed()

        assertThat(order.status).isEqualTo(OrderStatus.FAILED)
    }

    @Test
    fun expireChangesPaymentPendingToExpired() {
        val order = pendingOrder()

        order.expire()

        assertThat(order.status).isEqualTo(OrderStatus.EXPIRED)
    }

    @Test
    fun failedOrderCannotBeCanceledByUser() {
        val order = pendingOrder()
        order.markCompletionFailed()

        val ex = assertThrows<CoreException> {
            order.cancelByUser()
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun completedOrderCanBeCanceledBeforeShipping() {
        val order = pendingOrder()
        order.complete()

        order.cancelByUser()

        assertAll(
            { assertThat(order.status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(order.cancelReason).isEqualTo(OrderCancelReason.USER_REQUESTED) },
        )
    }
}
```

- [ ] **Step 2: Run order domain test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderTest"
```

Expected: FAIL because `OrderStatus.FAILED`, `OrderStatus.EXPIRED`, `Order.complete()`, `Order.markCompletionFailed()`, `Order.expire()`, and `Order.cancelByUser()` do not exist yet.

- [ ] **Step 3: Implement order states and entity transitions**

Replace `OrderStatus.kt`:

```kotlin
package com.loopers.domain.order

enum class OrderStatus {
    PAYMENT_PENDING,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELED,
    SHIPPING_STARTED,
}
```

In `Order.kt`, remove the `paymentTransactionId` property and replace transition methods with:

```kotlin
fun complete() {
    if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.FAILED) {
        throw CoreException(ErrorType.CONFLICT, "결제대기 또는 실패 주문만 완료할 수 있습니다.")
    }
    status = OrderStatus.COMPLETED
}

fun markCompletionFailed() {
    if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.FAILED) {
        throw CoreException(ErrorType.CONFLICT, "결제대기 또는 실패 주문만 실패 처리할 수 있습니다.")
    }
    status = OrderStatus.FAILED
}

fun expire() {
    if (status != OrderStatus.PAYMENT_PENDING) {
        throw CoreException(ErrorType.CONFLICT, "결제대기 주문만 만료할 수 있습니다.")
    }
    status = OrderStatus.EXPIRED
}

fun cancelByUser() {
    when (status) {
        OrderStatus.PAYMENT_PENDING,
        OrderStatus.COMPLETED,
        -> {
            cancelReason = OrderCancelReason.USER_REQUESTED
            status = OrderStatus.CANCELED
        }
        OrderStatus.FAILED,
        OrderStatus.EXPIRED,
        OrderStatus.CANCELED,
        OrderStatus.SHIPPING_STARTED,
        -> throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
    }
}

fun cancelByOperator(reason: OrderCancelReason) {
    if (status != OrderStatus.FAILED && status != OrderStatus.COMPLETED && status != OrderStatus.PAYMENT_PENDING) {
        throw CoreException(ErrorType.CONFLICT, "운영자 취소가 불가능한 주문 상태입니다.")
    }
    cancelReason = reason
    status = OrderStatus.CANCELED
}
```

In `OrderCommand.kt`, change `Pay`:

```kotlin
data class Pay(
    val orderId: Long,
    val paymentKey: String,
)
```

- [ ] **Step 4: Write failing reservation state tests**

Replace state tests in `StockReservationTest.kt`:

```kotlin
@Test
fun completeChangesInProgressToCompleted() {
    val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

    reservation.complete()

    assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED)
}

@Test
fun expireChangesInProgressToExpired() {
    val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

    reservation.expire()

    assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED)
}

@Test
fun cancelChangesInProgressToCanceled() {
    val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)

    reservation.cancel()

    assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED)
}

@Test
fun completedReservationCanBeCanceledAfterPaymentCancel() {
    val reservation = StockReservation(orderId = 1L, productId = 10L, quantity = 2)
    reservation.complete()

    reservation.cancel()

    assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED)
}
```

- [ ] **Step 5: Run reservation domain test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.StockReservationTest"
```

Expected: FAIL because `IN_PROGRESS`, `COMPLETED`, `EXPIRED`, and `complete()` do not exist.

- [ ] **Step 6: Implement reservation states and transitions**

Replace `StockReservationStatus.kt`:

```kotlin
package com.loopers.domain.order

enum class StockReservationStatus {
    IN_PROGRESS,
    EXPIRED,
    CANCELED,
    COMPLETED,
}
```

In `StockReservation.kt`, set default status and methods:

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 30)
var status: StockReservationStatus = StockReservationStatus.IN_PROGRESS,
```

```kotlin
fun complete() {
    if (status != StockReservationStatus.IN_PROGRESS) {
        throw CoreException(ErrorType.CONFLICT, "진행 중 예약만 확정할 수 있습니다.")
    }
    status = StockReservationStatus.COMPLETED
}

fun expire() {
    if (status != StockReservationStatus.IN_PROGRESS) {
        throw CoreException(ErrorType.CONFLICT, "진행 중 예약만 만료할 수 있습니다.")
    }
    status = StockReservationStatus.EXPIRED
}

fun cancel() {
    if (status != StockReservationStatus.IN_PROGRESS && status != StockReservationStatus.COMPLETED) {
        throw CoreException(ErrorType.CONFLICT, "진행 중 또는 확정 예약만 취소할 수 있습니다.")
    }
    status = StockReservationStatus.CANCELED
}
```

- [ ] **Step 7: Run domain tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderTest" --tests "com.loopers.domain.order.StockReservationTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/order apps/commerce-api/src/test/kotlin/com/loopers/domain/order
git commit -m "feat: add order v2 domain states"
```

---

## Task 2: Payment Projection And Event Model

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PgProvider.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentStatus.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEventType.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/Payment.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEvent.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/payment/PaymentEventRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentRepositoryImpl.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentEventJpaRepository.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentEventRepositoryImpl.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/domain/payment/PaymentTest.kt`

- [ ] **Step 1: Write failing payment entity tests**

Create `PaymentTest.kt`:

```kotlin
package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentTest {
    @Test
    fun readyPaymentStoresOrderProviderRequestIdAndAmount() {
        val payment = Payment(
            orderId = 1L,
            pgProvider = PgProvider.FAKE,
            paymentRequestId = "pay-req-1",
            requestedAmount = 3000L,
        )

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
            { assertThat(payment.completionRetryCount).isZero() },
            { assertThat(payment.paymentKey).isNull() },
        )
    }

    @Test
    fun approveRequestedStoresPaymentKey() {
        val payment = readyPayment()

        payment.recordApproveRequested("payment-key-1")

        assertThat(payment.paymentKey).isEqualTo("payment-key-1")
    }

    @Test
    fun approveStoresPgTransactionAndApprovedAmount() {
        val payment = readyPayment()
        payment.recordApproveRequested("payment-key-1")

        payment.approve(pgTransactionId = "pg-tx-1", approvedAmount = 3000L)

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.pgTransactionId).isEqualTo("pg-tx-1") },
            { assertThat(payment.approvedAmount).isEqualTo(3000L) },
            { assertThat(payment.approvedAt).isNotNull() },
        )
    }

    @Test
    fun completionFailureIncrementsOnlyWhenRequested() {
        val payment = readyPayment()
        payment.markCompletionFailed("stock conflict")

        payment.incrementCompletionRetryFailure("stock conflict again")

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
            { assertThat(payment.completionRetryCount).isEqualTo(1) },
            { assertThat(payment.failureReason).isEqualTo("stock conflict again") },
        )
    }

    @Test
    fun negativeRequestedAmountThrowsBadRequest() {
        val ex = assertThrows<CoreException> {
            Payment(
                orderId = 1L,
                pgProvider = PgProvider.FAKE,
                paymentRequestId = "pay-req-1",
                requestedAmount = -1L,
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    private fun readyPayment() = Payment(
        orderId = 1L,
        pgProvider = PgProvider.FAKE,
        paymentRequestId = "pay-req-1",
        requestedAmount = 3000L,
    )
}
```

- [ ] **Step 2: Run payment entity test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentTest"
```

Expected: FAIL because payment domain files do not exist.

- [ ] **Step 3: Create payment enums**

Create `PgProvider.kt`:

```kotlin
package com.loopers.domain.payment

enum class PgProvider {
    FAKE,
}
```

Create `PaymentStatus.kt`:

```kotlin
package com.loopers.domain.payment

enum class PaymentStatus {
    READY,
    APPROVED,
    VERIFY_FAILED,
    COMPLETION_FAILED,
    EXPIRED,
    CANCELED,
}
```

Create `PaymentEventType.kt`:

```kotlin
package com.loopers.domain.payment

enum class PaymentEventType {
    REQUEST_CREATED,
    APPROVE_REQUESTED,
    APPROVE_SUCCEEDED,
    APPROVE_FAILED,
    VERIFY_REQUESTED,
    VERIFY_SUCCEEDED,
    VERIFY_FAILED,
    CANCEL_REQUESTED,
    CANCEL_SUCCEEDED,
    CANCEL_FAILED,
    COMPLETION_FAILED,
    EXPIRED,
}
```

- [ ] **Step 4: Create Payment entity**

Create `Payment.kt`:

```kotlin
package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payments_order_id", columnNames = ["order_id"]),
        UniqueConstraint(name = "uk_payments_provider_payment_request_id", columnNames = ["pg_provider", "payment_request_id"]),
        UniqueConstraint(name = "uk_payments_provider_payment_key", columnNames = ["pg_provider", "payment_key"]),
        UniqueConstraint(name = "uk_payments_provider_pg_transaction_id", columnNames = ["pg_provider", "pg_transaction_id"]),
    ],
)
class Payment(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.READY,

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 30)
    val pgProvider: PgProvider,

    @Column(name = "payment_request_id", nullable = false, length = 100)
    val paymentRequestId: String,

    @Column(name = "requested_amount", nullable = false)
    val requestedAmount: Long,

    @Column(name = "payment_key", length = 100)
    var paymentKey: String? = null,

    @Column(name = "pg_transaction_id", length = 100)
    var pgTransactionId: String? = null,

    @Column(name = "approved_amount")
    var approvedAmount: Long? = null,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "completion_retry_count", nullable = false)
    var completionRetryCount: Int = 0,

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null,

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null,

    @Column(name = "last_failed_at")
    var lastFailedAt: LocalDateTime? = null,
) : BaseEntity() {
    init {
        if (paymentRequestId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "결제 요청 식별자는 비어있을 수 없습니다.")
        if (requestedAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "결제 요청 금액은 0 미만일 수 없습니다.")
        if (approvedAmount != null && approvedAmount!! < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "승인 금액은 0 미만일 수 없습니다.")
        }
        if (completionRetryCount < 0) throw CoreException(ErrorType.BAD_REQUEST, "완료 재시도 횟수는 0 미만일 수 없습니다.")
    }

    fun recordApproveRequested(paymentKey: String) {
        if (paymentKey.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "PG paymentKey는 비어있을 수 없습니다.")
        if (status != PaymentStatus.READY && status != PaymentStatus.VERIFY_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "승인 요청을 기록할 수 없는 결제 상태입니다.")
        }
        this.paymentKey = paymentKey
        failureReason = null
    }

    fun approve(pgTransactionId: String, approvedAmount: Long) {
        if (pgTransactionId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "PG 거래 식별자는 비어있을 수 없습니다.")
        if (approvedAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "승인 금액은 0 미만일 수 없습니다.")
        status = PaymentStatus.APPROVED
        this.pgTransactionId = pgTransactionId
        this.approvedAmount = approvedAmount
        approvedAt = LocalDateTime.now()
        failureReason = null
    }

    fun markVerifyFailed(reason: String) {
        status = PaymentStatus.VERIFY_FAILED
        failureReason = reason.take(500)
        lastFailedAt = LocalDateTime.now()
    }

    fun markCompletionFailed(reason: String) {
        status = PaymentStatus.COMPLETION_FAILED
        failureReason = reason.take(500)
        lastFailedAt = LocalDateTime.now()
    }

    fun incrementCompletionRetryFailure(reason: String) {
        completionRetryCount += 1
        markCompletionFailed(reason)
    }

    fun expire() {
        if (status != PaymentStatus.READY && status != PaymentStatus.VERIFY_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "만료할 수 없는 결제 상태입니다.")
        }
        status = PaymentStatus.EXPIRED
    }

    fun cancel() {
        if (status != PaymentStatus.READY && status != PaymentStatus.APPROVED && status != PaymentStatus.COMPLETION_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 결제 상태입니다.")
        }
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }
}
```

- [ ] **Step 5: Create PaymentEvent entity**

Create `PaymentEvent.kt`:

```kotlin
package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "payment_events")
class PaymentEvent(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "payment_id")
    val paymentId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    val eventType: PaymentEventType,

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 30)
    val pgProvider: PgProvider,

    @Column(name = "payment_request_id", nullable = false, length = 100)
    val paymentRequestId: String,

    @Column(name = "payment_key", length = 100)
    val paymentKey: String?,

    @Column(name = "pg_transaction_id", length = 100)
    val pgTransactionId: String?,

    @Column(name = "requested_amount", nullable = false)
    val requestedAmount: Long,

    @Column(name = "approved_amount")
    val approvedAmount: Long?,

    @Column(name = "pg_status", length = 50)
    val pgStatus: String?,

    @Column(name = "failure_reason", length = 500)
    val failureReason: String?,

    @Column(name = "raw_response_summary", length = 1000)
    val rawResponseSummary: String?,
) : BaseEntity()
```

- [ ] **Step 6: Create repository interfaces and implementations**

Create `PaymentRepository.kt`:

```kotlin
package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: Long): Payment?
    fun findByOrderIdForUpdate(orderId: Long): Payment?
    fun findCompletionFailedForRetry(limit: Int): List<Payment>
}
```

Create `PaymentEventRepository.kt`:

```kotlin
package com.loopers.domain.payment

interface PaymentEventRepository {
    fun save(event: PaymentEvent): PaymentEvent
    fun findByOrderId(orderId: Long): List<PaymentEvent>
}
```

Create `PaymentJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderIdAndDeletedAtIsNull(orderId: Long): Payment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select payment
          from Payment payment
         where payment.orderId = :orderId
           and payment.deletedAt is null
        """,
    )
    fun findByOrderIdForUpdate(@Param("orderId") orderId: Long): Payment?

    fun findTop100ByStatusAndCompletionRetryCountLessThanAndDeletedAtIsNullOrderByUpdatedAtAsc(
        status: PaymentStatus,
        completionRetryCount: Int,
    ): List<Payment>
}
```

Create `PaymentRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun findByOrderId(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)

    override fun findByOrderIdForUpdate(orderId: Long): Payment? =
        paymentJpaRepository.findByOrderIdForUpdate(orderId)

    override fun findCompletionFailedForRetry(limit: Int): List<Payment> =
        paymentJpaRepository
            .findTop100ByStatusAndCompletionRetryCountLessThanAndDeletedAtIsNullOrderByUpdatedAtAsc(
                PaymentStatus.COMPLETION_FAILED,
                3,
            )
            .take(limit)
}
```

Create `PaymentEventJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentEvent
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentEventJpaRepository : JpaRepository<PaymentEvent, Long> {
    fun findAllByOrderIdAndDeletedAtIsNullOrderByIdAsc(orderId: Long): List<PaymentEvent>
}
```

Create `PaymentEventRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentEventRepository
import org.springframework.stereotype.Component

@Component
class PaymentEventRepositoryImpl(
    private val paymentEventJpaRepository: PaymentEventJpaRepository,
) : PaymentEventRepository {
    override fun save(event: PaymentEvent): PaymentEvent = paymentEventJpaRepository.save(event)

    override fun findByOrderId(orderId: Long): List<PaymentEvent> =
        paymentEventJpaRepository.findAllByOrderIdAndDeletedAtIsNullOrderByIdAsc(orderId)
}
```

- [ ] **Step 7: Run payment domain test and verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.payment.PaymentTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/payment apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment apps/commerce-api/src/test/kotlin/com/loopers/domain/payment
git commit -m "feat: add payment projection and events"
```

---

## Task 3: Product Stock Reserved Quantity And Atomic Updates

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStock.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStockRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/catalog/ProductStockTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImplIntegrationTest.kt`

- [ ] **Step 1: Write failing ProductStock entity tests**

In `ProductStockTest.kt`, assert reserved quantity invariants:

```kotlin
@Test
fun initialReservedQuantityDefaultsToZero() {
    val stock = ProductStock(productId = 1L, stockQuantity = 10)

    assertAll(
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(stock.availableQuantity()).isEqualTo(10) },
    )
}

@Test
fun reservedQuantityCannotBeNegative() {
    val ex = assertThrows<CoreException> {
        ProductStock(productId = 1L, stockQuantity = 10, reservedQuantity = -1)
    }

    assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
}
```

- [ ] **Step 2: Run ProductStock test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.catalog.ProductStockTest"
```

Expected: FAIL because `reservedQuantity` and `availableQuantity()` do not exist.

- [ ] **Step 3: Add reserved quantity to ProductStock**

Change constructor and helper in `ProductStock.kt`:

```kotlin
@Column(name = "reserved_quantity", nullable = false)
var reservedQuantity: Int = 0,
```

```kotlin
init {
    if (stockQuantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 미만일 수 없습니다.")
    if (reservedQuantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "예약 재고 수량은 0 미만일 수 없습니다.")
}

fun availableQuantity(): Int = stockQuantity - reservedQuantity
```

Keep `add`, `deduct`, and `restore` for admin stock changes. They change actual stock only.

- [ ] **Step 4: Write failing atomic update integration tests**

Create `ProductStockRepositoryImplIntegrationTest.kt`:

```kotlin
package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductStockRepositoryImplIntegrationTest @Autowired constructor(
    private val productStockRepository: ProductStockRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun reserveIfAvailableIncreasesReservedQuantityOnlyWhenAvailableStockIsEnough() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))

        val first = productStockRepository.reserveIfAvailable(productId = 10L, quantity = 3)
        val second = productStockRepository.reserveIfAvailable(productId = 10L, quantity = 3)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(first).isTrue() },
            { assertThat(second).isFalse() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isEqualTo(3) },
        )
    }

    @Test
    fun confirmReservedDecreasesActualAndReservedTogether() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5, reservedQuantity = 3))

        val confirmed = productStockRepository.confirmReserved(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(confirmed).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isEqualTo(1) },
        )
    }

    @Test
    fun releaseReservedDecreasesOnlyReservedQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5, reservedQuantity = 3))

        val released = productStockRepository.releaseReserved(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(released).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isEqualTo(1) },
        )
    }

    @Test
    fun restoreActualStockIncreasesOnlyActualQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 3, reservedQuantity = 0))

        val restored = productStockRepository.restoreActualStock(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(restored).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
        )
    }
}
```

- [ ] **Step 5: Run atomic update test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.catalog.ProductStockRepositoryImplIntegrationTest"
```

Expected: FAIL because repository methods do not exist.

- [ ] **Step 6: Add repository atomic update methods**

Replace `ProductStockRepository.kt` with:

```kotlin
package com.loopers.domain.catalog

interface ProductStockRepository {
    fun save(stock: ProductStock): ProductStock
    fun findByProductId(productId: Long): ProductStock?
    fun lockAllByProductIds(productIds: Collection<Long>): List<ProductStock>
    fun deductIfEnough(productId: Long, quantity: Int): Boolean
    fun reserveIfAvailable(productId: Long, quantity: Int): Boolean
    fun confirmReserved(productId: Long, quantity: Int): Boolean
    fun releaseReserved(productId: Long, quantity: Int): Boolean
    fun restoreActualStock(productId: Long, quantity: Int): Boolean
}
```

Add these methods to `ProductStockJpaRepository.kt`:

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update ProductStock stock
       set stock.reservedQuantity = stock.reservedQuantity + :quantity
     where stock.productId = :productId
       and stock.deletedAt is null
       and stock.stockQuantity - stock.reservedQuantity >= :quantity
    """,
)
fun reserveIfAvailable(
    @Param("productId") productId: Long,
    @Param("quantity") quantity: Int,
): Int

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update ProductStock stock
       set stock.stockQuantity = stock.stockQuantity - :quantity,
           stock.reservedQuantity = stock.reservedQuantity - :quantity
     where stock.productId = :productId
       and stock.deletedAt is null
       and stock.stockQuantity >= :quantity
       and stock.reservedQuantity >= :quantity
    """,
)
fun confirmReserved(
    @Param("productId") productId: Long,
    @Param("quantity") quantity: Int,
): Int

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update ProductStock stock
       set stock.reservedQuantity = stock.reservedQuantity - :quantity
     where stock.productId = :productId
       and stock.deletedAt is null
       and stock.reservedQuantity >= :quantity
    """,
)
fun releaseReserved(
    @Param("productId") productId: Long,
    @Param("quantity") quantity: Int,
): Int

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update ProductStock stock
       set stock.stockQuantity = stock.stockQuantity + :quantity
     where stock.productId = :productId
       and stock.deletedAt is null
    """,
)
fun restoreActualStock(
    @Param("productId") productId: Long,
    @Param("quantity") quantity: Int,
): Int
```

Add implementations to `ProductStockRepositoryImpl.kt`:

```kotlin
override fun reserveIfAvailable(productId: Long, quantity: Int): Boolean =
    productStockJpaRepository.reserveIfAvailable(productId, quantity) == 1

override fun confirmReserved(productId: Long, quantity: Int): Boolean =
    productStockJpaRepository.confirmReserved(productId, quantity) == 1

override fun releaseReserved(productId: Long, quantity: Int): Boolean =
    productStockJpaRepository.releaseReserved(productId, quantity) == 1

override fun restoreActualStock(productId: Long, quantity: Int): Boolean =
    productStockJpaRepository.restoreActualStock(productId, quantity) == 1
```

- [ ] **Step 7: Run stock tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.catalog.ProductStockTest" --tests "com.loopers.infrastructure.catalog.ProductStockRepositoryImplIntegrationTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStock.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/catalog/ProductStockRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockJpaRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImpl.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/catalog/ProductStockTest.kt apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/catalog/ProductStockRepositoryImplIntegrationTest.kt
git commit -m "feat: add atomic reserved stock updates"
```

---

## Task 4: Reservation Service With Atomic Stock Updates

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/CatalogStockPort.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CatalogOrderStockAdapter.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservationRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/StockApplicationService.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/StockApplicationServiceTest.kt`

- [ ] **Step 1: Write failing service tests for reserve and confirm**

Replace existing `StockApplicationServiceTest` expectations with v2 statuses and reserved quantity:

```kotlin
@Test
fun reserveAllCreatesInProgressReservationsAndIncreasesReservedQuantity() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    productStockJpaRepository.save(ProductStock(productId = 20L, stockQuantity = 3))

    stockApplicationService.reserveAll(
        orderId = 1L,
        items = listOf(
            OrderCommand.CheckoutItem(20L, "상품B", "브랜드B", 2000L, 2),
            OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 1),
        ),
    )

    val reservations = stockReservationJpaRepository.findAllByOrderId(1L)
    val firstStock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val secondStock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(20L)!!
    assertAll(
        { assertThat(reservations).hasSize(2) },
        { assertThat(reservations).allMatch { it.status == StockReservationStatus.IN_PROGRESS } },
        { assertThat(firstStock.stockQuantity).isEqualTo(5) },
        { assertThat(firstStock.reservedQuantity).isEqualTo(1) },
        { assertThat(secondStock.stockQuantity).isEqualTo(3) },
        { assertThat(secondStock.reservedQuantity).isEqualTo(2) },
    )
}

@Test
fun confirmAndDeductChangesReservationToCompletedAndDecreasesActualAndReservedStock() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    stockApplicationService.reserveAll(
        orderId = 1L,
        items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
    )

    stockApplicationService.confirmAndDeduct(orderId = 1L)

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
    assertAll(
        { assertThat(stock.stockQuantity).isEqualTo(3) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
    )
}
```

- [ ] **Step 2: Add failing tests for cancel, expire, and post-payment cancel**

Append:

```kotlin
@Test
fun cancelInProgressReleasesReservedQuantityAndCancelsReservation() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    stockApplicationService.reserveAll(
        orderId = 1L,
        items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
    )

    stockApplicationService.cancelInProgress(orderId = 1L)

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
    assertAll(
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
    )
}

@Test
fun expireInProgressReleasesReservedQuantityAndExpiresReservation() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    stockApplicationService.reserveAll(
        orderId = 1L,
        items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
    )

    stockApplicationService.expireInProgress(orderId = 1L)

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
    assertAll(
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED) },
    )
}

@Test
fun cancelCompletedRestoresActualStockAndCancelsCompletedReservation() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    stockApplicationService.reserveAll(
        orderId = 1L,
        items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
    )
    stockApplicationService.confirmAndDeduct(orderId = 1L)

    stockApplicationService.cancelCompletedAndRestore(orderId = 1L)

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
    assertAll(
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
    )
}
```

- [ ] **Step 3: Run service test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.StockApplicationServiceTest"
```

Expected: FAIL because port methods, repository transitions, and service methods are not implemented.

- [ ] **Step 4: Replace CatalogStockPort with atomic operations**

Use this shape in `CatalogStockPort.kt`:

```kotlin
package com.loopers.application.order

interface CatalogStockPort {
    fun reserveAll(quantitiesByProductId: Map<Long, Int>)
    fun confirmReservedAll(quantitiesByProductId: Map<Long, Int>)
    fun releaseReservedAll(quantitiesByProductId: Map<Long, Int>)
    fun restoreActualAll(quantitiesByProductId: Map<Long, Int>)
}
```

Replace `CatalogOrderStockAdapter.kt` body with:

```kotlin
@Component
class CatalogOrderStockAdapter(
    private val productStockRepository: ProductStockRepository,
) : CatalogStockPort {
    override fun reserveAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.reserveIfAvailable(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "재고가 부족합니다.")
            }
        }
    }

    override fun confirmReservedAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.confirmReserved(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "예약 재고 확정에 실패했습니다.")
            }
        }
    }

    override fun releaseReservedAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.releaseReserved(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "예약 재고 반환에 실패했습니다.")
            }
        }
    }

    override fun restoreActualAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.restoreActualStock(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "실재고 복구에 실패했습니다.")
            }
        }
    }
}
```

- [ ] **Step 5: Replace StockReservationRepository contract**

Replace `StockReservationRepository.kt` with:

```kotlin
package com.loopers.domain.order

interface StockReservationRepository {
    fun saveAll(reservations: List<StockReservation>): List<StockReservation>
    fun findByOrderId(orderId: Long): List<StockReservation>
    fun findByOrderIdAndStatus(orderId: Long, status: StockReservationStatus): List<StockReservation>
    fun transitionByOrderId(orderId: Long, currentStatus: StockReservationStatus, nextStatus: StockReservationStatus): Int
}
```

Add to `StockReservationJpaRepository.kt`:

```kotlin
fun findAllByOrderIdAndStatusAndDeletedAtIsNull(orderId: Long, status: StockReservationStatus): List<StockReservation>

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update StockReservation reservation
       set reservation.status = :nextStatus
     where reservation.orderId = :orderId
       and reservation.status = :currentStatus
       and reservation.deletedAt is null
    """,
)
fun transitionByOrderId(
    @Param("orderId") orderId: Long,
    @Param("currentStatus") currentStatus: StockReservationStatus,
    @Param("nextStatus") nextStatus: StockReservationStatus,
): Int
```

In `StockReservationRepositoryImpl.kt`, implement:

```kotlin
override fun findByOrderIdAndStatus(orderId: Long, status: StockReservationStatus): List<StockReservation> =
    stockReservationJpaRepository.findAllByOrderIdAndStatusAndDeletedAtIsNull(orderId, status)

override fun transitionByOrderId(
    orderId: Long,
    currentStatus: StockReservationStatus,
    nextStatus: StockReservationStatus,
): Int =
    stockReservationJpaRepository.transitionByOrderId(orderId, currentStatus, nextStatus)
```

Remove active-sum methods from this repository. Availability now comes from `product_stocks.reserved_quantity`.

- [ ] **Step 6: Replace StockApplicationService logic**

Use this implementation shape:

```kotlin
@Transactional
fun reserveAll(orderId: Long, items: List<OrderCommand.CheckoutItem>) {
    if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 품목은 비어있을 수 없습니다.")
    val mergedItems = mergeItems(items)

    catalogStockPort.reserveAll(mergedItems)
    stockReservationRepository.saveAll(
        mergedItems.map { (productId, quantity) ->
            StockReservation(orderId = orderId, productId = productId, quantity = quantity)
        },
    )
}

@Transactional
fun confirmAndDeduct(orderId: Long) {
    val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "확정할 진행 중 예약이 없습니다.")
    val quantitiesByProductId = quantitiesByProductId(reservations)

    catalogStockPort.confirmReservedAll(quantitiesByProductId)
    transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.COMPLETED, reservations.size)
}

@Transactional
fun expireInProgress(orderId: Long) {
    val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "만료할 진행 중 예약이 없습니다.")
    transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.EXPIRED, reservations.size)
    catalogStockPort.releaseReservedAll(quantitiesByProductId(reservations))
}

@Transactional
fun cancelInProgress(orderId: Long) {
    val reservations = requireReservations(orderId, StockReservationStatus.IN_PROGRESS, "취소할 진행 중 예약이 없습니다.")
    transition(orderId, StockReservationStatus.IN_PROGRESS, StockReservationStatus.CANCELED, reservations.size)
    catalogStockPort.releaseReservedAll(quantitiesByProductId(reservations))
}

@Transactional
fun cancelCompletedAndRestore(orderId: Long) {
    val reservations = requireReservations(orderId, StockReservationStatus.COMPLETED, "취소할 확정 예약이 없습니다.")
    catalogStockPort.restoreActualAll(quantitiesByProductId(reservations))
    transition(orderId, StockReservationStatus.COMPLETED, StockReservationStatus.CANCELED, reservations.size)
}

@Transactional(readOnly = true)
fun findInProgress(orderId: Long): List<StockReservation> =
    stockReservationRepository.findByOrderIdAndStatus(orderId, StockReservationStatus.IN_PROGRESS)

private fun mergeItems(items: List<OrderCommand.CheckoutItem>): Map<Long, Int> =
    items.groupBy { it.productId }
        .mapValues { entry -> entry.value.sumOf { it.quantity } }
        .toSortedMap()

private fun requireReservations(
    orderId: Long,
    status: StockReservationStatus,
    message: String,
): List<StockReservation> {
    val reservations = stockReservationRepository.findByOrderIdAndStatus(orderId, status)
    if (reservations.isEmpty()) throw CoreException(ErrorType.CONFLICT, message)
    return reservations
}

private fun quantitiesByProductId(reservations: List<StockReservation>): Map<Long, Int> =
    reservations.groupBy { it.productId }
        .mapValues { entry -> entry.value.sumOf { it.quantity } }
        .toSortedMap()

private fun transition(
    orderId: Long,
    currentStatus: StockReservationStatus,
    nextStatus: StockReservationStatus,
    expectedCount: Int,
) {
    val updatedCount = stockReservationRepository.transitionByOrderId(orderId, currentStatus, nextStatus)
    if (updatedCount != expectedCount) {
        throw CoreException(ErrorType.CONFLICT, "예약 상태가 변경되어 요청을 처리할 수 없습니다.")
    }
}
```

- [ ] **Step 7: Run reservation service test and verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.StockApplicationServiceTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/CatalogStockPort.kt apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CatalogOrderStockAdapter.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/order/StockReservationRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationJpaRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationRepositoryImpl.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/StockApplicationService.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/StockApplicationServiceTest.kt
git commit -m "feat: process reservations with atomic stock updates"
```

---

## Task 5: Order Repository V2 Transitions

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderApplicationServiceTest.kt` if it exists; otherwise add coverage through facade tests in Task 8.

- [ ] **Step 1: Replace OrderRepository contract**

Use this interface:

```kotlin
package com.loopers.domain.order

import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order
    fun saveItems(items: List<OrderItem>): List<OrderItem>
    fun findById(orderId: Long): Order?
    fun findItemsByOrderId(orderId: Long): List<OrderItem>
    fun findByProductId(productId: Long): List<Order>
    fun completeFromPaymentPending(orderId: Long): Int
    fun completeFromFailed(orderId: Long): Int
    fun markCompletionFailed(orderId: Long): Int
    fun markCompletedAsFailed(orderId: Long): Int
    fun expirePaymentPending(orderId: Long): Int
    fun cancelPaymentPending(orderId: Long, reason: OrderCancelReason): Int
    fun cancelCompleted(orderId: Long, reason: OrderCancelReason): Int
    fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): Int
    fun startShippingCompleted(orderId: Long): Int
    fun findExpiredPaymentPending(now: LocalDateTime): List<Order>
}
```

- [ ] **Step 2: Replace OrderJpaRepository update queries**

Remove `paymentTransactionId` update. Add:

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    """
    update OrderEntity orderEntity
       set orderEntity.status = :nextStatus
     where orderEntity.id = :orderId
       and orderEntity.status = :currentStatus
       and orderEntity.deletedAt is null
    """,
)
fun updateStatus(
    @Param("orderId") orderId: Long,
    @Param("currentStatus") currentStatus: OrderStatus,
    @Param("nextStatus") nextStatus: OrderStatus,
): Int
```

Keep `cancelByCurrentStatus` but use it for `PAYMENT_PENDING`, `COMPLETED`, and operator `FAILED` cancellation. `findAllByStatusAndReservationExpiresAtBefore(OrderStatus.PAYMENT_PENDING, now)` remains valid and excludes `FAILED`.

- [ ] **Step 3: Implement OrderRepositoryImpl mappings**

Map each method to the query:

```kotlin
override fun completeFromPaymentPending(orderId: Long): Int =
    orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.COMPLETED)

override fun completeFromFailed(orderId: Long): Int =
    orderJpaRepository.updateStatus(orderId, OrderStatus.FAILED, OrderStatus.COMPLETED)

override fun markCompletionFailed(orderId: Long): Int =
    orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED)

override fun markCompletedAsFailed(orderId: Long): Int =
    orderJpaRepository.updateStatus(orderId, OrderStatus.COMPLETED, OrderStatus.FAILED)

override fun expirePaymentPending(orderId: Long): Int =
    orderJpaRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.EXPIRED)

override fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): Int =
    orderJpaRepository.cancelByCurrentStatus(orderId, reason, OrderStatus.FAILED, OrderStatus.CANCELED)
```

- [ ] **Step 4: Replace OrderApplicationService transition methods**

Use explicit v2 methods:

```kotlin
@Transactional
fun completePaymentPending(orderId: Long): OrderInfo.Detail {
    requireUpdated(orderRepository.completeFromPaymentPending(orderId))
    return getDetail(orderId)
}

@Transactional
fun completeFailed(orderId: Long): OrderInfo.Detail {
    requireUpdated(orderRepository.completeFromFailed(orderId))
    return getDetail(orderId)
}

@Transactional
fun markCompletionFailed(orderId: Long): OrderInfo.Detail {
    requireUpdated(orderRepository.markCompletionFailed(orderId))
    return getDetail(orderId)
}

@Transactional
fun markCompletedAsFailed(orderId: Long): OrderInfo.Detail {
    requireUpdated(orderRepository.markCompletedAsFailed(orderId))
    return getDetail(orderId)
}

@Transactional
fun expirePaymentPending(orderId: Long): OrderInfo.Detail {
    requireUpdated(orderRepository.expirePaymentPending(orderId))
    return getDetail(orderId)
}
```

Keep `cancelPaymentPending`, `cancelCompleted`, and `startShipping`. Add `cancelFailedByOperator` only for admin/CS flows:

```kotlin
@Transactional
fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): OrderInfo.Detail {
    requireUpdated(orderRepository.cancelFailedByOperator(orderId, reason))
    return getDetail(orderId)
}
```

- [ ] **Step 5: Remove order-owned payment transaction from info**

In `OrderInfo.Detail`, remove `paymentTransactionId` or keep it as nullable projection set by caller. If preserving response compatibility, use:

```kotlin
val paymentTransactionId: String? = null,
```

and in `from`:

```kotlin
paymentTransactionId = null,
```

This keeps order table clean while allowing Task 8 to enrich response from payment projection if needed.

- [ ] **Step 6: Run affected compile tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest"
```

Expected: FAIL at this point because checkout facade still uses old methods and old status names. This confirms Task 5 changed the contract and Task 8 must update orchestration.

- [ ] **Step 7: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderJpaRepository.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/OrderRepositoryImpl.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderApplicationService.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt
git commit -m "feat: add order v2 repository transitions"
```

---

## Task 6: Payment Application Service And Fake Gateway

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentCommand.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentGateway.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/FakePaymentGateway.kt`
- Delete after migration: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentGateway.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentApplicationServiceTest.kt`

- [ ] **Step 1: Write failing payment service test**

Create `PaymentApplicationServiceTest.kt`:

```kotlin
package com.loopers.application.payment

import com.loopers.domain.payment.PaymentEventRepository
import com.loopers.domain.payment.PaymentEventType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentApplicationServiceTest @Autowired constructor(
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun createReadyPaymentAppendsRequestCreatedEvent() {
        val payment = paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)

        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
            { assertThat(payment.paymentRequestId).startsWith("order-1-") },
            { assertThat(events.map { it.eventType }).containsExactly(PaymentEventType.REQUEST_CREATED) },
        )
    }

    @Test
    fun recordApproveRequestedStoresPaymentKeyAndAppendsEvent() {
        paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)

        paymentApplicationService.recordApproveRequested(orderId = 1L, paymentKey = "payment-key-1")

        val payment = paymentRepository.findByOrderId(1L)!!
        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.paymentKey).isEqualTo("payment-key-1") },
            { assertThat(events.map { it.eventType }).containsExactly(PaymentEventType.REQUEST_CREATED, PaymentEventType.APPROVE_REQUESTED) },
        )
    }

    @Test
    fun recordApproveSucceededApprovesPaymentAndAppendsEvent() {
        paymentApplicationService.createReady(orderId = 1L, requestedAmount = 3000L)
        paymentApplicationService.recordApproveRequested(orderId = 1L, paymentKey = "payment-key-1")

        paymentApplicationService.recordApproveSucceeded(
            orderId = 1L,
            pgTransactionId = "pg-tx-1",
            approvedAmount = 3000L,
            pgStatus = "APPROVED",
            rawResponseSummary = "fake approved",
        )

        val payment = paymentRepository.findByOrderId(1L)!!
        val events = paymentEventRepository.findByOrderId(1L)
        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.pgTransactionId).isEqualTo("pg-tx-1") },
            { assertThat(events.last().eventType).isEqualTo(PaymentEventType.APPROVE_SUCCEEDED) },
        )
    }
}
```

- [ ] **Step 2: Run payment service test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.PaymentApplicationServiceTest"
```

Expected: FAIL because application payment files do not exist.

- [ ] **Step 3: Create PaymentCommand and PaymentGateway**

Create `PaymentCommand.kt`:

```kotlin
package com.loopers.application.payment

import com.loopers.domain.payment.PgProvider

class PaymentCommand {
    /**
     * Approve는 사용자가 PG 결제를 마친 직후, 클라이언트가 전달한 paymentKey를 서버가 PG에 제출해
     * 이 결제를 우리 주문의 결제로 승인해도 되는지 확인하고 승인 처리하는 요청이다.
     */
    data class Approve(
        val orderId: Long,
        val paymentRequestId: String,
        val paymentKey: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )

    /**
     * Verify는 이미 승인되었거나 승인되었을 가능성이 있는 결제 건을 기준으로 PG의 현재 결제 상태와
     * 금액, 주문 식별자를 재검증하는 요청이다. 새 결제를 만들거나 중복 승인하는 목적이 아니다.
     */
    data class Verify(
        val orderId: Long,
        val paymentRequestId: String,
        val paymentKey: String?,
        val pgTransactionId: String?,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )

    data class Cancel(
        val orderId: Long,
        val pgTransactionId: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )
}
```

Create `PaymentGateway.kt`:

```kotlin
package com.loopers.application.payment

interface PaymentGateway {
    fun approve(command: PaymentCommand.Approve): PgResult
    fun verify(command: PaymentCommand.Verify): PgResult
    fun cancel(command: PaymentCommand.Cancel): PgResult

    data class PgResult(
        val success: Boolean,
        val pgStatus: String,
        val pgTransactionId: String?,
        val approvedAmount: Long?,
        val failureReason: String?,
        val rawResponseSummary: String,
    )
}
```

- [ ] **Step 4: Create PaymentInfo and PaymentApplicationService**

Create `PaymentInfo.kt`:

```kotlin
package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgProvider

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val pgProvider: PgProvider,
    val paymentRequestId: String,
    val paymentKey: String?,
    val pgTransactionId: String?,
    val requestedAmount: Long,
    val approvedAmount: Long?,
    val completionRetryCount: Int,
) {
    companion object {
        fun from(payment: Payment) = PaymentInfo(
            paymentId = payment.id,
            orderId = payment.orderId,
            status = payment.status,
            pgProvider = payment.pgProvider,
            paymentRequestId = payment.paymentRequestId,
            paymentKey = payment.paymentKey,
            pgTransactionId = payment.pgTransactionId,
            requestedAmount = payment.requestedAmount,
            approvedAmount = payment.approvedAmount,
            completionRetryCount = payment.completionRetryCount,
        )
    }
}
```

Create `PaymentApplicationService.kt` with methods:

```kotlin
@Transactional
fun createReady(orderId: Long, requestedAmount: Long): PaymentInfo {
    val payment = paymentRepository.save(
        Payment(
            orderId = orderId,
            pgProvider = PgProvider.FAKE,
            paymentRequestId = "order-$orderId-${UUID.randomUUID()}",
            requestedAmount = requestedAmount,
        ),
    )
    appendEvent(payment, PaymentEventType.REQUEST_CREATED, pgStatus = null, failureReason = null, rawResponseSummary = "payment request created")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordApproveRequested(orderId: Long, paymentKey: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.recordApproveRequested(paymentKey)
    appendEvent(payment, PaymentEventType.APPROVE_REQUESTED, pgStatus = null, failureReason = null, rawResponseSummary = "approve requested")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordApproveSucceeded(
    orderId: Long,
    pgTransactionId: String,
    approvedAmount: Long,
    pgStatus: String,
    rawResponseSummary: String,
): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.requestedAmount != approvedAmount) {
        payment.markVerifyFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
        appendEvent(payment, PaymentEventType.APPROVE_FAILED, pgStatus, payment.failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }
    payment.approve(pgTransactionId, approvedAmount)
    appendEvent(payment, PaymentEventType.APPROVE_SUCCEEDED, pgStatus, null, rawResponseSummary)
    return PaymentInfo.from(payment)
}

@Transactional
fun recordApproveFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.markVerifyFailed(failureReason)
    appendEvent(payment, PaymentEventType.APPROVE_FAILED, pgStatus, failureReason, rawResponseSummary)
    return PaymentInfo.from(payment)
}

@Transactional(readOnly = true)
fun getByOrderId(orderId: Long): PaymentInfo =
    PaymentInfo.from(
        paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."),
    )
```

Add the remaining state/event methods in the same service:

```kotlin
@Transactional
fun recordVerifyRequested(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.status != PaymentStatus.COMPLETION_FAILED) {
        throw CoreException(ErrorType.CONFLICT, "완료 실패 결제만 검증 재시도할 수 있습니다.")
    }
    if (payment.completionRetryCount >= 3) {
        throw CoreException(ErrorType.CONFLICT, "결제 완료 재시도 횟수를 초과했습니다.")
    }
    appendEvent(payment, PaymentEventType.VERIFY_REQUESTED, null, null, "verify requested")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordVerifySucceeded(
    orderId: Long,
    pgTransactionId: String,
    approvedAmount: Long,
    pgStatus: String,
    rawResponseSummary: String,
): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.requestedAmount != approvedAmount) {
        payment.markVerifyFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
        appendEvent(payment, PaymentEventType.VERIFY_FAILED, pgStatus, payment.failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }
    payment.approve(pgTransactionId, approvedAmount)
    appendEvent(payment, PaymentEventType.VERIFY_SUCCEEDED, pgStatus, null, rawResponseSummary)
    return PaymentInfo.from(payment)
}

@Transactional
fun recordVerifyFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.markVerifyFailed(failureReason)
    appendEvent(payment, PaymentEventType.VERIFY_FAILED, pgStatus, failureReason, rawResponseSummary)
    return PaymentInfo.from(payment)
}

@Transactional
fun markCompletionFailed(orderId: Long, reason: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.markCompletionFailed(reason)
    appendEvent(payment, PaymentEventType.COMPLETION_FAILED, null, reason, "internal completion failed")
    return PaymentInfo.from(payment)
}

@Transactional
fun incrementCompletionRetryFailure(orderId: Long, reason: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.incrementCompletionRetryFailure(reason)
    appendEvent(payment, PaymentEventType.COMPLETION_FAILED, null, reason, "internal completion retry failed")
    return PaymentInfo.from(payment)
}

@Transactional
fun expire(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.expire()
    appendEvent(payment, PaymentEventType.EXPIRED, null, null, "reservation expired")
    return PaymentInfo.from(payment)
}

@Transactional
fun cancelReady(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.cancel()
    appendEvent(payment, PaymentEventType.CANCEL_SUCCEEDED, null, null, "payment canceled before pg approve")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordCancelRequested(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.status != PaymentStatus.APPROVED) {
        throw CoreException(ErrorType.CONFLICT, "승인된 결제만 취소할 수 있습니다.")
    }
    appendEvent(payment, PaymentEventType.CANCEL_REQUESTED, null, null, "cancel requested")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordCancelSucceeded(orderId: Long, pgStatus: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.cancel()
    appendEvent(payment, PaymentEventType.CANCEL_SUCCEEDED, pgStatus, null, rawResponseSummary)
    return PaymentInfo.from(payment)
}

@Transactional
fun recordCancelFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.markCompletionFailed(failureReason)
    appendEvent(payment, PaymentEventType.CANCEL_FAILED, pgStatus, failureReason, rawResponseSummary)
    return PaymentInfo.from(payment)
}

private fun getPaymentForUpdate(orderId: Long): Payment =
    paymentRepository.findByOrderIdForUpdate(orderId)
        ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")
```

Use this private event helper exactly:

```kotlin
private fun appendEvent(
    payment: Payment,
    eventType: PaymentEventType,
    pgStatus: String?,
    failureReason: String?,
    rawResponseSummary: String?,
) {
    paymentEventRepository.save(
        PaymentEvent(
            orderId = payment.orderId,
            paymentId = payment.id.takeIf { it != 0L },
            eventType = eventType,
            pgProvider = payment.pgProvider,
            paymentRequestId = payment.paymentRequestId,
            paymentKey = payment.paymentKey,
            pgTransactionId = payment.pgTransactionId,
            requestedAmount = payment.requestedAmount,
            approvedAmount = payment.approvedAmount,
            pgStatus = pgStatus,
            failureReason = failureReason?.take(500),
            rawResponseSummary = rawResponseSummary?.take(1000),
        ),
    )
}
```

- [ ] **Step 5: Replace FakePaymentGateway**

Change imports to `com.loopers.application.payment.PaymentCommand` and `PaymentGateway`. Implement:

```kotlin
@Component
class FakePaymentGateway : PaymentGateway {
    private var failNextApproval: Boolean = false
    private var failNextVerify: Boolean = false
    private var failNextCancel: Boolean = false
    val canceledTransactionIds: MutableList<String> = mutableListOf()
    val transactionActiveDuringApprove: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringVerify: MutableList<Boolean> = mutableListOf()
    val transactionActiveDuringCancel: MutableList<Boolean> = mutableListOf()

    override fun approve(command: PaymentCommand.Approve): PaymentGateway.PgResult {
        transactionActiveDuringApprove.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextApproval) {
            failNextApproval = false
            return PaymentGateway.PgResult(false, "REJECTED", null, null, "결제 승인에 실패했습니다.", "fake approval rejected")
        }
        return PaymentGateway.PgResult(
            success = true,
            pgStatus = "APPROVED",
            pgTransactionId = "payment-${command.orderId}",
            approvedAmount = command.amount,
            failureReason = null,
            rawResponseSummary = "fake approval approved",
        )
    }

    override fun verify(command: PaymentCommand.Verify): PaymentGateway.PgResult {
        transactionActiveDuringVerify.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextVerify) {
            failNextVerify = false
            return PaymentGateway.PgResult(false, "VERIFY_FAILED", null, null, "결제 검증에 실패했습니다.", "fake verify rejected")
        }
        return PaymentGateway.PgResult(
            success = true,
            pgStatus = "APPROVED",
            pgTransactionId = command.pgTransactionId ?: "payment-${command.orderId}",
            approvedAmount = command.amount,
            failureReason = null,
            rawResponseSummary = "fake verify approved",
        )
    }

    override fun cancel(command: PaymentCommand.Cancel): PaymentGateway.PgResult {
        transactionActiveDuringCancel.add(TransactionSynchronizationManager.isActualTransactionActive())
        if (failNextCancel) {
            failNextCancel = false
            return PaymentGateway.PgResult(false, "CANCEL_FAILED", null, null, "결제 취소에 실패했습니다.", "fake cancel rejected")
        }
        canceledTransactionIds.add(command.pgTransactionId)
        return PaymentGateway.PgResult(true, "CANCELED", command.pgTransactionId, command.amount, null, "fake cancel approved")
    }

    fun failNextApproval() {
        failNextApproval = true
    }

    fun failNextVerify() {
        failNextVerify = true
    }

    fun failNextCancel() {
        failNextCancel = true
    }

    fun reset() {
        failNextApproval = false
        failNextVerify = false
        failNextCancel = false
        canceledTransactionIds.clear()
        transactionActiveDuringApprove.clear()
        transactionActiveDuringVerify.clear()
        transactionActiveDuringCancel.clear()
    }
}
```

Add imports:

```kotlin
import org.springframework.transaction.support.TransactionSynchronizationManager
```

- [ ] **Step 6: Delete old order PaymentGateway**

Remove `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentGateway.kt` after all imports move to `application.payment.PaymentGateway`.

- [ ] **Step 7: Run payment service tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.payment.PaymentApplicationServiceTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/payment apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment apps/commerce-api/src/test/kotlin/com/loopers/application/payment
git rm apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentGateway.kt
git commit -m "feat: add payment application service and fake pg"
```

---

## Task 7: Checkout Creates Payment Request And Reserves Stock

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AcceptanceV1ApiE2ETest.kt`

- [ ] **Step 1: Write failing checkout integration expectation**

In `OrderCheckoutFacadeIntegrationTest.checkoutRollbackLeavesNoOrderOrReservationWhenReservationFails`, also assert no payment is created:

```kotlin
{ assertThat(paymentJpaRepository.count()).isZero() },
```

Add `PaymentJpaRepository` to constructor:

```kotlin
private val paymentJpaRepository: PaymentJpaRepository,
```

Add a checkout success test:

```kotlin
@Test
fun checkoutCreatesPaymentReadyAndInProgressReservation() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))

    val checkout = facade.checkout(checkoutCommand())

    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    assertAll(
        { assertThat(checkout.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
        { assertThat(payment.requestedAmount).isEqualTo(2000L) },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isEqualTo(2) },
    )
}
```

- [ ] **Step 2: Run checkout integration test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.checkoutCreatesPaymentReadyAndInProgressReservation"
```

Expected: FAIL because checkout does not create payment projection yet.

- [ ] **Step 3: Inject PaymentApplicationService and create payment after order**

Change `OrderCheckoutFacade` constructor:

```kotlin
class OrderCheckoutFacade(
    private val orderApplicationService: OrderApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
    private val paymentGateway: PaymentGateway,
    private val paymentCompletionApplicationService: PaymentCompletionApplicationService,
)
```

Use imports from `application.payment`.

Change checkout:

```kotlin
@Transactional
fun checkout(command: OrderCommand.Checkout): OrderInfo.Detail {
    val order = orderApplicationService.createPending(command)
    stockApplicationService.reserveAll(order.orderId, command.items)
    paymentApplicationService.createReady(order.orderId, requestedAmount(command.items))
    return orderApplicationService.getDetail(order.orderId)
}

private fun requestedAmount(items: List<OrderCommand.CheckoutItem>): Long =
    items.sumOf { it.priceSnapshot * it.quantity }
```

The transaction order is order row, reserved stock update, reservation rows, payment row, payment event. If reservation fails, order and payment roll back.

- [ ] **Step 4: Run checkout integration test and verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.checkoutCreatesPaymentReadyAndInProgressReservation" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.checkoutRollbackLeavesNoOrderOrReservationWhenReservationFails"
```

Expected: PASS.

- [ ] **Step 5: Update acceptance helpers from active reservation sum to reservedQuantity**

In `AcceptanceV1ApiE2ETest`, change helper expectations and helper method:

```kotlin
private fun activeReservedQuantity(productId: Long): Int =
    productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)?.reservedQuantity ?: 0
```

Update status assertions:

```kotlin
it.status == StockReservationStatus.IN_PROGRESS
```

and confirmed status assertions:

```kotlin
it.status == StockReservationStatus.COMPLETED
```

- [ ] **Step 6: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AcceptanceV1ApiE2ETest.kt
git commit -m "feat: create payment request during checkout"
```

---

## Task 8: Payment Approve Flow With External PG Outside Transactions

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`

- [ ] **Step 1: Write failing tests for approve transaction boundary and success**

Replace payment success test with:

```kotlin
@Test
fun paymentSuccessRunsPgApproveOutsideTransactionAndCompletesOrder() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())

    val paid = facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    assertAll(
        { assertThat(paid.status).isEqualTo(OrderStatus.COMPLETED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
        { assertThat(payment.paymentKey).isEqualTo("payment-key-${checkout.orderId}") },
        { assertThat(payment.pgTransactionId).isEqualTo("payment-${checkout.orderId}") },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
        { assertThat(stock.stockQuantity).isEqualTo(3) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(paymentGateway.transactionActiveDuringApprove).containsExactly(false) },
    )
}
```

Add imports for `PaymentStatus`.

- [ ] **Step 2: Write failing internal completion failure test**

Append:

```kotlin
@Test
fun approvedPaymentWithInternalCompletionFailureLeavesOrderFailedPaymentCompletionFailedAndReservationInProgress() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())
    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    stock.reservedQuantity = 0
    productStockJpaRepository.saveAndFlush(stock)

    val ex = assertThrows<CoreException> {
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
    }

    val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    assertAll(
        { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
        { assertThat(order.status).isEqualTo(OrderStatus.FAILED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
        { assertThat(payment.completionRetryCount).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
    )
}
```

- [ ] **Step 3: Run approve tests and verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.paymentSuccessRunsPgApproveOutsideTransactionAndCompletesOrder" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.approvedPaymentWithInternalCompletionFailureLeavesOrderFailedPaymentCompletionFailedAndReservationInProgress"
```

Expected: FAIL because `pay` still has `@Transactional` and does not use payment projection.

- [ ] **Step 4: Create PaymentCompletionApplicationService**

Create:

```kotlin
package com.loopers.application.order

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.domain.order.OrderCancelReason
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentCompletionApplicationService(
    private val orderApplicationService: OrderApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val paymentApplicationService: PaymentApplicationService,
) {
    @Transactional
    fun completePaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.confirmAndDeduct(orderId)
        return orderApplicationService.completePaymentPending(orderId)
    }

    @Transactional
    fun completeFailed(orderId: Long): OrderInfo.Detail {
        stockApplicationService.confirmAndDeduct(orderId)
        return orderApplicationService.completeFailed(orderId)
    }

    @Transactional
    fun markCompletionFailed(orderId: Long, reason: String): OrderInfo.Detail {
        orderApplicationService.markCompletionFailed(orderId)
        paymentApplicationService.markCompletionFailed(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }

    @Transactional
    fun markCompletedCancelRecoveryFailed(orderId: Long, reason: String): OrderInfo.Detail {
        orderApplicationService.markCompletedAsFailed(orderId)
        paymentApplicationService.markCompletionFailed(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }

    @Transactional
    fun expirePaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.expireInProgress(orderId)
        val expired = orderApplicationService.expirePaymentPending(orderId)
        paymentApplicationService.expire(orderId)
        return expired
    }

    @Transactional
    fun cancelPaymentPending(orderId: Long): OrderInfo.Detail {
        stockApplicationService.cancelInProgress(orderId)
        val canceled = orderApplicationService.cancelPaymentPending(orderId, OrderCancelReason.USER_REQUESTED)
        paymentApplicationService.cancelReady(orderId)
        return canceled
    }

    @Transactional
    fun cancelCompletedAfterPgSuccess(orderId: Long, pgStatus: String, rawResponseSummary: String): OrderInfo.Detail {
        stockApplicationService.cancelCompletedAndRestore(orderId)
        val canceled = orderApplicationService.cancelCompleted(orderId, OrderCancelReason.USER_REQUESTED)
        paymentApplicationService.recordCancelSucceeded(orderId, pgStatus, rawResponseSummary)
        return canceled
    }

    @Transactional
    fun incrementRetryFailure(orderId: Long, reason: String): OrderInfo.Detail {
        paymentApplicationService.incrementCompletionRetryFailure(orderId, reason)
        return orderApplicationService.getDetail(orderId)
    }
}
```

- [ ] **Step 5: Replace pay orchestration**

Remove `@Transactional` from `OrderCheckoutFacade.pay`.

Implement:

```kotlin
fun pay(command: OrderCommand.Pay): OrderInfo.Detail {
    val order = orderApplicationService.getOrder(command.orderId)
    return when (order.status) {
        OrderStatus.COMPLETED -> orderApplicationService.getDetail(order.id)
        OrderStatus.PAYMENT_PENDING -> approvePending(command, order)
        OrderStatus.FAILED -> retryFailedCompletion(order.id)
        OrderStatus.EXPIRED,
        OrderStatus.CANCELED,
        OrderStatus.SHIPPING_STARTED,
        -> throw CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다.")
    }
}
```

Add helpers:

```kotlin
private fun approvePending(command: OrderCommand.Pay, order: Order): OrderInfo.Detail {
    if (order.reservationExpiresAt.isBefore(LocalDateTime.now())) {
        paymentCompletionApplicationService.expirePaymentPending(order.id)
        throw CoreException(ErrorType.CONFLICT, "예약이 만료되었습니다.")
    }

    val requested = paymentApplicationService.recordApproveRequested(order.id, command.paymentKey)
    val pgResult = paymentGateway.approve(
        PaymentCommand.Approve(
            orderId = order.id,
            paymentRequestId = requested.paymentRequestId,
            paymentKey = command.paymentKey,
            amount = requested.requestedAmount,
        ),
    )
    if (!pgResult.success || pgResult.pgTransactionId == null || pgResult.approvedAmount == null) {
        paymentApplicationService.recordApproveFailed(
            orderId = order.id,
            pgStatus = pgResult.pgStatus,
            failureReason = pgResult.failureReason ?: "PG 승인에 실패했습니다.",
            rawResponseSummary = pgResult.rawResponseSummary,
        )
        throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 승인에 실패했습니다.")
    }

    paymentApplicationService.recordApproveSucceeded(
        orderId = order.id,
        pgTransactionId = pgResult.pgTransactionId,
        approvedAmount = pgResult.approvedAmount,
        pgStatus = pgResult.pgStatus,
        rawResponseSummary = pgResult.rawResponseSummary,
    )

    return runCatching {
        paymentCompletionApplicationService.completePaymentPending(order.id)
    }.getOrElse { throwable ->
        paymentCompletionApplicationService.markCompletionFailed(order.id, throwable.message ?: throwable.javaClass.simpleName)
        throw throwable
    }
}
```

The helper calls `recordApproveRequested` in its own transaction, calls PG outside any transaction, records PG result in its own transaction, and then runs internal completion in its own transaction.

- [ ] **Step 6: Run approve tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.paymentSuccessRunsPgApproveOutsideTransactionAndCompletesOrder" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.approvedPaymentWithInternalCompletionFailureLeavesOrderFailedPaymentCompletionFailedAndReservationInProgress"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt
git commit -m "feat: separate pg approve from order completion transaction"
```

---

## Task 9: Expiration And User Cancel Flows

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`

- [ ] **Step 1: Update failing expiration test**

Replace expiry expectation:

```kotlin
@Test
fun expireReservationsExpiresPendingOrderReservationAndPaymentAndReleasesReservedQuantity() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand(LocalDateTime.of(2026, 5, 29, 12, 0)))

    facade.expireReservations(OrderCommand.Expire(LocalDateTime.of(2026, 5, 29, 12, 1)))

    val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    assertAll(
        { assertThat(order.status).isEqualTo(OrderStatus.EXPIRED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.EXPIRED) },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED) },
        { assertThat(stock.reservedQuantity).isZero() },
    )
}
```

- [ ] **Step 2: Update failing pre-payment cancel test**

Replace expectation:

```kotlin
@Test
fun cancelBeforePaymentCancelsOrderPaymentReservationAndReleasesReservedQuantityWithoutPgCancel() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())

    val canceled = facade.cancel(OrderCommand.Cancel(checkout.orderId))

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    assertAll(
        { assertThat(canceled.status).isEqualTo(OrderStatus.CANCELED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED) },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(paymentGateway.canceledTransactionIds).isEmpty() },
    )
}
```

- [ ] **Step 3: Run cancel and expiry tests and verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.expireReservationsExpiresPendingOrderReservationAndPaymentAndReleasesReservedQuantity" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.cancelBeforePaymentCancelsOrderPaymentReservationAndReleasesReservedQuantityWithoutPgCancel"
```

Expected: FAIL because old `cancelExpired` sets order `CANCELED` and does not update payment.

- [ ] **Step 4: Implement expiration transaction in PaymentCompletionApplicationService**

Add this method to `PaymentCompletionApplicationService`. Do not put `@Transactional` on a private `OrderCheckoutFacade` helper because self-invocation will bypass Spring's transaction proxy.

```kotlin
@Transactional
fun expirePaymentPending(orderId: Long): OrderInfo.Detail {
    stockApplicationService.expireInProgress(orderId)
    val expired = orderApplicationService.expirePaymentPending(orderId)
    paymentApplicationService.expire(orderId)
    return expired
}
```

Change public expiration:

```kotlin
fun expireReservations(command: OrderCommand.Expire): Int =
    orderApplicationService.findExpiredPaymentPending(command.now).count { order ->
        paymentCompletionApplicationService.expirePaymentPending(order.id)
        true
    }
```

The query only returns `PAYMENT_PENDING`, so `FAILED` is excluded.

- [ ] **Step 5: Implement pre-payment cancel transaction**

Change `cancel` branch for `PAYMENT_PENDING`:

```kotlin
OrderStatus.PAYMENT_PENDING -> paymentCompletionApplicationService.cancelPaymentPending(command.orderId)
```

Add this method to `PaymentCompletionApplicationService`:

```kotlin
@Transactional
fun cancelPaymentPending(orderId: Long): OrderInfo.Detail {
    stockApplicationService.cancelInProgress(orderId)
    val canceled = orderApplicationService.cancelPaymentPending(orderId, OrderCancelReason.USER_REQUESTED)
    paymentApplicationService.cancelReady(orderId)
    return canceled
}
```

`paymentApplicationService.cancelReady` is defined in Task 6 and appends `CANCEL_SUCCEEDED` with raw summary `"payment canceled before pg approve"`.

- [ ] **Step 6: Run tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.expireReservationsExpiresPendingOrderReservationAndPaymentAndReleasesReservedQuantity" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.cancelBeforePaymentCancelsOrderPaymentReservationAndReleasesReservedQuantityWithoutPgCancel"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt
git commit -m "feat: expire and cancel pending orders with reserved stock release"
```

---

## Task 10: Post-Payment Cancel With PG Cancel Outside Transaction

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`

- [ ] **Step 1: Update completed cancel test**

Replace completed cancel test:

```kotlin
@Test
fun cancelAfterPaymentRunsPgCancelOutsideTransactionRestoresStockAndCancelsReservation() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())
    facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))

    val canceled = facade.cancel(OrderCommand.Cancel(checkout.orderId))

    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    assertAll(
        { assertThat(canceled.status).isEqualTo(OrderStatus.CANCELED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED) },
        { assertThat(stock.stockQuantity).isEqualTo(5) },
        { assertThat(stock.reservedQuantity).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
        { assertThat(paymentGateway.canceledTransactionIds).containsExactly("payment-${checkout.orderId}") },
        { assertThat(paymentGateway.transactionActiveDuringCancel).containsExactly(false) },
    )
}
```

- [ ] **Step 2: Add PG cancel success but DB restore failure test**

Append:

```kotlin
@Test
fun pgCancelSuccessWithRestoreFailureLeavesOrderFailedAndPaymentCompletionFailed() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())
    facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
    stockReservationJpaRepository.deleteAll(stockReservationJpaRepository.findAllByOrderId(checkout.orderId))
    stockReservationJpaRepository.flush()

    val ex = assertThrows<CoreException> {
        facade.cancel(OrderCommand.Cancel(checkout.orderId))
    }

    val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    assertAll(
        { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
        { assertThat(order.status).isEqualTo(OrderStatus.FAILED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
    )
}
```

- [ ] **Step 3: Run completed cancel tests and verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.cancelAfterPaymentRunsPgCancelOutsideTransactionRestoresStockAndCancelsReservation" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.pgCancelSuccessWithRestoreFailureLeavesOrderFailedAndPaymentCompletionFailed"
```

Expected: FAIL because current cancel calls PG inside a transaction and keeps reservation completed.

- [ ] **Step 4: Add cancel preparation and success methods to PaymentApplicationService**

Add:

```kotlin
@Transactional
fun recordCancelRequested(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.status != PaymentStatus.APPROVED) {
        throw CoreException(ErrorType.CONFLICT, "승인된 결제만 취소할 수 있습니다.")
    }
    appendEvent(payment, PaymentEventType.CANCEL_REQUESTED, pgStatus = null, failureReason = null, rawResponseSummary = "cancel requested")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordCancelSucceeded(orderId: Long, pgStatus: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    payment.cancel()
    appendEvent(payment, PaymentEventType.CANCEL_SUCCEEDED, pgStatus, null, rawResponseSummary)
    return PaymentInfo.from(payment)
}
```

- [ ] **Step 5: Replace completed cancel orchestration**

Remove `@Transactional` from public `cancel` if it is still present. Route `COMPLETED` to:

```kotlin
private fun cancelCompleted(orderId: Long): OrderInfo.Detail {
    val payment = paymentApplicationService.recordCancelRequested(orderId)
    val pgTransactionId = payment.pgTransactionId
        ?: throw CoreException(ErrorType.CONFLICT, "PG 거래 식별자가 없는 결제는 취소할 수 없습니다.")
    val pgResult = paymentGateway.cancel(
        PaymentCommand.Cancel(
            orderId = orderId,
            pgTransactionId = pgTransactionId,
            amount = payment.approvedAmount ?: payment.requestedAmount,
        ),
    )
    if (!pgResult.success) {
        paymentApplicationService.recordCancelFailed(
            orderId = orderId,
            pgStatus = pgResult.pgStatus,
            failureReason = pgResult.failureReason ?: "PG 취소에 실패했습니다.",
            rawResponseSummary = pgResult.rawResponseSummary,
        )
        throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 취소에 실패했습니다.")
    }

    return runCatching {
        paymentCompletionApplicationService.cancelCompletedAfterPgSuccess(orderId, pgResult.pgStatus, pgResult.rawResponseSummary)
    }.getOrElse { throwable ->
        paymentCompletionApplicationService.markCompletedCancelRecoveryFailed(orderId, throwable.message ?: throwable.javaClass.simpleName)
        throw throwable
    }
}
```

Add this transaction method to `PaymentCompletionApplicationService`:

```kotlin
@Transactional
fun cancelCompletedAfterPgSuccess(orderId: Long, pgStatus: String, rawResponseSummary: String): OrderInfo.Detail {
    stockApplicationService.cancelCompletedAndRestore(orderId)
    val canceled = orderApplicationService.cancelCompleted(orderId, OrderCancelReason.USER_REQUESTED)
    paymentApplicationService.recordCancelSucceeded(orderId, pgStatus, rawResponseSummary)
    return canceled
}
```

- [ ] **Step 6: Run completed cancel tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.cancelAfterPaymentRunsPgCancelOutsideTransactionRestoresStockAndCancelsReservation" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.pgCancelSuccessWithRestoreFailureLeavesOrderFailedAndPaymentCompletionFailed"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt
git commit -m "feat: cancel approved payments outside db transactions"
```

---

## Task 11: Failed Completion Retry With PG Verify

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`

- [ ] **Step 1: Add failing retry success test**

Append:

```kotlin
@Test
fun failedCompletionCanRetryAfterReservationExpiresWhenPgVerifySucceeds() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand(LocalDateTime.now().minusMinutes(1)))
    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    stock.reservedQuantity = 0
    productStockJpaRepository.saveAndFlush(stock)
    assertThrows<CoreException> {
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
    }
    stock.reservedQuantity = 2
    productStockJpaRepository.saveAndFlush(stock)

    val retried = facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))

    val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
    assertAll(
        { assertThat(retried.status).isEqualTo(OrderStatus.COMPLETED) },
        { assertThat(order.status).isEqualTo(OrderStatus.COMPLETED) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
        { assertThat(payment.completionRetryCount).isZero() },
        { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
        { assertThat(paymentGateway.transactionActiveDuringVerify).containsExactly(false) },
    )
}
```

- [ ] **Step 2: Add failing retry count stop test**

Append:

```kotlin
@Test
fun failedCompletionRetryFailureIncrementsCountAndStopsAtThree() {
    productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
    val checkout = facade.checkout(checkoutCommand())
    val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
    stock.reservedQuantity = 0
    productStockJpaRepository.saveAndFlush(stock)
    assertThrows<CoreException> {
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
    }

    repeat(3) {
        assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))
        }
    }
    val ex = assertThrows<CoreException> {
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))
    }

    val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
    assertAll(
        { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
        { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
        { assertThat(payment.completionRetryCount).isEqualTo(3) },
    )
}
```

- [ ] **Step 3: Run retry tests and verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.failedCompletionCanRetryAfterReservationExpiresWhenPgVerifySucceeds" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.failedCompletionRetryFailureIncrementsCountAndStopsAtThree"
```

Expected: FAIL because FAILED retry is not implemented.

- [ ] **Step 4: Add verify methods to PaymentApplicationService**

Implement:

```kotlin
@Transactional
fun recordVerifyRequested(orderId: Long): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.status != PaymentStatus.COMPLETION_FAILED) {
        throw CoreException(ErrorType.CONFLICT, "완료 실패 결제만 검증 재시도할 수 있습니다.")
    }
    if (payment.completionRetryCount >= 3) {
        throw CoreException(ErrorType.CONFLICT, "결제 완료 재시도 횟수를 초과했습니다.")
    }
    appendEvent(payment, PaymentEventType.VERIFY_REQUESTED, null, null, "verify requested")
    return PaymentInfo.from(payment)
}

@Transactional
fun recordVerifySucceeded(orderId: Long, pgTransactionId: String, approvedAmount: Long, pgStatus: String, rawResponseSummary: String): PaymentInfo {
    val payment = getPaymentForUpdate(orderId)
    if (payment.requestedAmount != approvedAmount) {
        payment.markVerifyFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
        appendEvent(payment, PaymentEventType.VERIFY_FAILED, pgStatus, payment.failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }
    payment.approve(pgTransactionId, approvedAmount)
    appendEvent(payment, PaymentEventType.VERIFY_SUCCEEDED, pgStatus, null, rawResponseSummary)
    return PaymentInfo.from(payment)
}
```

- [ ] **Step 5: Implement failed retry orchestration**

In `OrderCheckoutFacade`, add:

```kotlin
private fun retryFailedCompletion(orderId: Long): OrderInfo.Detail {
    val payment = paymentApplicationService.recordVerifyRequested(orderId)
    val pgResult = paymentGateway.verify(
        PaymentCommand.Verify(
            orderId = orderId,
            paymentRequestId = payment.paymentRequestId,
            paymentKey = payment.paymentKey,
            pgTransactionId = payment.pgTransactionId,
            amount = payment.requestedAmount,
        ),
    )
    if (!pgResult.success || pgResult.pgTransactionId == null || pgResult.approvedAmount == null) {
        paymentApplicationService.recordVerifyFailed(
            orderId = orderId,
            pgStatus = pgResult.pgStatus,
            failureReason = pgResult.failureReason ?: "PG 검증에 실패했습니다.",
            rawResponseSummary = pgResult.rawResponseSummary,
        )
        throw CoreException(ErrorType.BAD_REQUEST, pgResult.failureReason ?: "결제 검증에 실패했습니다.")
    }
    paymentApplicationService.recordVerifySucceeded(
        orderId = orderId,
        pgTransactionId = pgResult.pgTransactionId,
        approvedAmount = pgResult.approvedAmount,
        pgStatus = pgResult.pgStatus,
        rawResponseSummary = pgResult.rawResponseSummary,
    )

    return runCatching {
        paymentCompletionApplicationService.completeFailed(orderId)
    }.getOrElse { throwable ->
        paymentCompletionApplicationService.incrementRetryFailure(orderId, throwable.message ?: throwable.javaClass.simpleName)
        throw throwable
    }
}
```

In `PaymentCompletionApplicationService.incrementRetryFailure`, log an error when count reaches 3. Use `LoggerFactory.getLogger(javaClass)` and include:

```kotlin
private val logger = LoggerFactory.getLogger(javaClass)

@Transactional
fun incrementRetryFailure(orderId: Long, reason: String): OrderInfo.Detail {
    val payment = paymentApplicationService.incrementCompletionRetryFailure(orderId, reason)
    if (payment.completionRetryCount >= 3) {
        val reservations = stockApplicationService.findInProgress(orderId)
        val productQuantities = reservations
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
        logger.error(
            "payment completion retry stopped orderId={} paymentId={} pgProvider={} pgTransactionId={} reservationIds={} productQuantities={} reason={} retryCount={}",
            orderId,
            payment.paymentId,
            payment.pgProvider,
            payment.pgTransactionId,
            reservations.map { it.id },
            productQuantities,
            reason,
            payment.completionRetryCount,
        )
    }
    return orderApplicationService.getDetail(orderId)
}
```

- [ ] **Step 6: Run retry tests and verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.failedCompletionCanRetryAfterReservationExpiresWhenPgVerifySucceeds" --tests "com.loopers.application.order.OrderCheckoutFacadeIntegrationTest.failedCompletionRetryFailureIncrementsCountAndStopsAtThree"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCheckoutFacade.kt apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentCompletionApplicationService.kt apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt
git commit -m "feat: retry failed payment completion with pg verify"
```

---

## Task 12: Catalog And Cart Availability From Reserved Quantity

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CatalogInfo.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/CatalogProductQueryDao.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/ProductQueryFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/CartCatalogAdapter.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/catalog/port/OrderReservationQueryPort.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/catalog/ProductQueryFacadeTest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/shopping/CartFacadeTest.kt`

- [ ] **Step 1: Update ProductDisplayRow model**

Change `CatalogInfo.ProductDisplayRow`:

```kotlin
data class ProductDisplayRow(
    val productId: Long,
    val productName: String,
    val brandId: Long,
    val brandName: String,
    val price: Long,
    val likeCount: Long,
    val stockQuantity: Int,
    val reservedQuantity: Int,
) {
    val availableQuantity: Int
        get() = stockQuantity - reservedQuantity
}
```

- [ ] **Step 2: Update CatalogProductQueryDao select clauses**

Every query selecting product display rows must select `stock.reservedQuantity` after `stock.stockQuantity`:

```sql
select p.id, p.name, b.id, b.name, p.price, stats.likeCount, stock.stockQuantity, stock.reservedQuantity
```

Every mapper must pass:

```kotlin
stockQuantity = values[6] as Int,
reservedQuantity = values[7] as Int,
```

- [ ] **Step 3: Update soldOut and cart orderable calculation**

In `ProductQueryFacade`, remove `OrderReservationQueryPort` constructor dependency and use:

```kotlin
soldOut = row.availableQuantity <= 0
```

for both list and detail.

In `CartCatalogAdapter`, map:

```kotlin
stockQuantity = availableQuantity,
orderable = availableQuantity > 0,
```

- [ ] **Step 4: Remove reservation sum query port usage**

Delete `OrderReservationQueryPort.kt` if no other class uses it. Remove `OrderReservationQueryPort` implementation from `StockReservationRepositoryImpl`. Verify with:

```bash
rg "OrderReservationQueryPort|getActiveReservedQuantity|sumActiveQuantity" apps/commerce-api/src/main/kotlin apps/commerce-api/src/test/kotlin
```

Expected: no references remain.

- [ ] **Step 5: Run catalog and shopping tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.catalog.ProductQueryFacadeTest" --tests "com.loopers.application.shopping.CartFacadeTest" --tests "com.loopers.interfaces.api.catalog.CatalogV1ApiE2ETest"
```

Expected: PASS after fixtures include `reservedQuantity = 0` or constructor default.

- [ ] **Step 6: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/catalog apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/catalog/CatalogProductQueryDao.kt apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/StockReservationRepositoryImpl.kt apps/commerce-api/src/test/kotlin/com/loopers/application/catalog apps/commerce-api/src/test/kotlin/com/loopers/application/shopping apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/catalog
git commit -m "feat: derive catalog availability from reserved stock"
```

---

## Task 13: API Minimal Wiring For Payment Key

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1ApiSpec.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Controller.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AcceptanceV1ApiE2ETest.kt`

- [ ] **Step 1: Add PayRequest DTO**

In `OrderV1Dto.kt`:

```kotlin
data class PayRequest(
    @field:NotBlank
    val paymentKey: String,
) {
    fun toCommand(orderId: Long): OrderCommand.Pay = OrderCommand.Pay(
        orderId = orderId,
        paymentKey = paymentKey,
    )
}
```

Keep `OrderResponse.paymentTransactionId` only if existing acceptance tests still assert it. If kept, document it as payment projection value and keep nullable.

- [ ] **Step 2: Change controller payment endpoint**

In `OrderV1Controller.pay`:

```kotlin
override fun pay(
    @CurrentUser user: User,
    @PathVariable orderId: Long,
    @RequestBody @Valid request: OrderV1Dto.PayRequest,
): ApiResponse<OrderV1Dto.OrderResponse> =
    orderCheckoutFacade.pay(request.toCommand(orderId))
        .let(OrderV1Dto.OrderResponse::from)
        .let(ApiResponse.Companion::success)
```

Update `OrderV1ApiSpec` signature to include `@RequestBody @Valid request: OrderV1Dto.PayRequest`.

- [ ] **Step 3: Update API tests and helpers**

Every test helper that posts to `/{orderId}/payment` must send:

```kotlin
mapOf("paymentKey" to "payment-key-$orderId")
```

For example:

```kotlin
private fun pay(loginId: String, orderId: Long): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> =
    post(
        "/api/v1/orders/$orderId/payment",
        mapOf("paymentKey" to "payment-key-$orderId"),
        authHeaders(loginId),
    )
```

- [ ] **Step 4: Run order API tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.order.OrderV1ApiE2ETest" --tests "com.loopers.interfaces.api.AcceptanceV1ApiE2ETest"
```

Expected: PASS after all old `OrderCommand.Pay(orderId)` calls are replaced.

- [ ] **Step 5: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AcceptanceV1ApiE2ETest.kt
git commit -m "feat: accept payment key for order payment"
```

---

## Task 14: Batch Expiration And Completion Retry Jobs

**Files:**
- Create: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/OrderReservationExpirationJobConfig.kt`
- Create: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/step/OrderReservationExpirationTasklet.kt`
- Create: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/PaymentCompletionRetryJobConfig.kt`
- Create: `apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order/step/PaymentCompletionRetryTasklet.kt`
- Create: `apps/commerce-batch/src/test/kotlin/com/loopers/job/order/OrderReservationExpirationJobE2ETest.kt`
- Create: `apps/commerce-batch/src/test/kotlin/com/loopers/job/order/PaymentCompletionRetryJobE2ETest.kt`

- [ ] **Step 1: Create expiration job config**

Use the existing demo job style. `OrderReservationExpirationJobConfig.kt`:

```kotlin
package com.loopers.batch.job.order

import com.loopers.batch.job.order.step.OrderReservationExpirationTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = OrderReservationExpirationJobConfig.JOB_NAME)
@Configuration
class OrderReservationExpirationJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val tasklet: OrderReservationExpirationTasklet,
) {
    companion object {
        const val JOB_NAME = "orderReservationExpirationJob"
        private const val STEP_NAME = "orderReservationExpirationStep"
    }

    @Bean(JOB_NAME)
    fun orderReservationExpirationJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(orderReservationExpirationStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun orderReservationExpirationStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(tasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
```

- [ ] **Step 2: Create expiration tasklet with one transaction per order**

`OrderReservationExpirationTasklet.kt`:

```kotlin
package com.loopers.batch.job.order.step

import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.LocalDateTime

@Component
class OrderReservationExpirationTasklet(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val now = LocalDateTime.now()
        val orderIds = jdbcTemplate.queryForList(
            """
            select id
              from orders
             where status = 'PAYMENT_PENDING'
               and reservation_expires_at <= :now
               and deleted_at is null
            """.trimIndent(),
            mapOf("now" to Timestamp.valueOf(now)),
            Long::class.java,
        )
        orderIds.forEach { orderId ->
            transactionTemplate.executeWithoutResult {
                expireOrder(orderId)
            }
        }
        return RepeatStatus.FINISHED
    }

    private fun expireOrder(orderId: Long) {
        val reservations = jdbcTemplate.queryForList(
            """
            select id, product_id, quantity
              from stock_reservations
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        if (reservations.isEmpty()) return

        val updatedReservations = jdbcTemplate.update(
            """
            update stock_reservations
               set status = 'EXPIRED', updated_at = now()
             where order_id = :orderId
               and status = 'IN_PROGRESS'
               and deleted_at is null
            """.trimIndent(),
            mapOf("orderId" to orderId),
        )
        require(updatedReservations == reservations.size) { "reservation affected row mismatch orderId=$orderId" }

        reservations.groupBy { it["product_id"] as Number }
            .mapValues { entry -> entry.value.sumOf { (it["quantity"] as Number).toInt() } }
            .forEach { (productId, quantity) ->
                val updatedStock = jdbcTemplate.update(
                    """
                    update product_stocks
                       set reserved_quantity = reserved_quantity - :quantity,
                           updated_at = now()
                     where product_id = :productId
                       and reserved_quantity >= :quantity
                       and deleted_at is null
                    """.trimIndent(),
                    mapOf("productId" to productId.toLong(), "quantity" to quantity),
                )
                require(updatedStock == 1) { "stock release affected row mismatch orderId=$orderId productId=$productId" }
            }

        jdbcTemplate.update(
            "update orders set status = 'EXPIRED', updated_at = now() where id = :orderId and status = 'PAYMENT_PENDING'",
            mapOf("orderId" to orderId),
        )
        jdbcTemplate.update(
            "update payments set status = 'EXPIRED', updated_at = now() where order_id = :orderId and status in ('READY', 'VERIFY_FAILED')",
            mapOf("orderId" to orderId),
        )
        appendPaymentEvent(orderId, "EXPIRED", "reservation expired")
    }

    private fun appendPaymentEvent(orderId: Long, eventType: String, rawResponseSummary: String) {
        val payment = jdbcTemplate.queryForMap(
            "select * from payments where order_id = :orderId and deleted_at is null",
            mapOf("orderId" to orderId),
        )
        jdbcTemplate.update(
            """
            insert into payment_events (
                order_id, payment_id, event_type, pg_provider, payment_request_id, payment_key, pg_transaction_id,
                requested_amount, approved_amount, pg_status, failure_reason, raw_response_summary, created_at, updated_at
            ) values (
                :orderId, :paymentId, :eventType, :pgProvider, :paymentRequestId, :paymentKey, :pgTransactionId,
                :requestedAmount, :approvedAmount, :pgStatus, :failureReason, :rawResponseSummary, now(), now()
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("paymentId", payment["id"])
                .addValue("eventType", eventType)
                .addValue("pgProvider", payment["pg_provider"])
                .addValue("paymentRequestId", payment["payment_request_id"])
                .addValue("paymentKey", payment["payment_key"])
                .addValue("pgTransactionId", payment["pg_transaction_id"])
                .addValue("requestedAmount", payment["requested_amount"])
                .addValue("approvedAmount", payment["approved_amount"])
                .addValue("pgStatus", null)
                .addValue("failureReason", null)
                .addValue("rawResponseSummary", rawResponseSummary),
        )
    }
}
```

- [ ] **Step 3: Create retry job config and tasklet**

Create `PaymentCompletionRetryJobConfig.kt`:

```kotlin
package com.loopers.batch.job.order

import com.loopers.batch.job.order.step.PaymentCompletionRetryTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = PaymentCompletionRetryJobConfig.JOB_NAME)
@Configuration
class PaymentCompletionRetryJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val tasklet: PaymentCompletionRetryTasklet,
) {
    companion object {
        const val JOB_NAME = "paymentCompletionRetryJob"
        private const val STEP_NAME = "paymentCompletionRetryStep"
    }

    @Bean(JOB_NAME)
    fun paymentCompletionRetryJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(paymentCompletionRetryStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun paymentCompletionRetryStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(tasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
```

Create `PaymentCompletionRetryTasklet.kt` with this order selection:

```kotlin
val rows = jdbcTemplate.queryForList(
    """
    select p.order_id
      from payments p
      join orders o on o.id = p.order_id
     where o.status = 'FAILED'
       and p.status = 'COMPLETION_FAILED'
       and p.completion_retry_count < 3
       and p.deleted_at is null
       and o.deleted_at is null
     order by p.updated_at asc
     limit 100
    """.trimIndent(),
    emptyMap<String, Any>(),
)
```

For each row, call a local fake verify equivalent that treats `pg_transaction_id` or `"payment-$orderId"` as approved. Then run the same SQL sequence as API completion retry:

```sql
update product_stocks
   set stock_quantity = stock_quantity - :quantity,
       reserved_quantity = reserved_quantity - :quantity,
       updated_at = now()
 where product_id = :productId
   and stock_quantity >= :quantity
   and reserved_quantity >= :quantity
   and deleted_at is null
```

Then:

```sql
update stock_reservations
   set status = 'COMPLETED', updated_at = now()
 where order_id = :orderId
   and status = 'IN_PROGRESS'
   and deleted_at is null
```

Then:

```sql
update orders set status = 'COMPLETED', updated_at = now() where id = :orderId and status = 'FAILED'
update payments set status = 'APPROVED', failure_reason = null, updated_at = now() where order_id = :orderId and status = 'COMPLETION_FAILED'
```

On failure inside the order transaction:

```sql
update payments
   set completion_retry_count = completion_retry_count + 1,
       last_failed_at = now(),
       failure_reason = :reason,
       updated_at = now()
 where order_id = :orderId
   and status = 'COMPLETION_FAILED'
```

Append `VERIFY_REQUESTED`, `VERIFY_SUCCEEDED`, `COMPLETION_FAILED` events using the same insert shape from the expiration tasklet. If `completion_retry_count + 1 >= 3`, log error with orderId, paymentId, pgProvider, pgTransactionId, reservationIds, product quantities, reason, and stack trace.

- [ ] **Step 4: Write batch E2E tests**

`OrderReservationExpirationJobE2ETest` should seed rows with `JdbcTemplate` or JPA repositories and assert:

```kotlin
assertAll(
    { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
    { assertThat(orderStatus(orderId)).isEqualTo("EXPIRED") },
    { assertThat(paymentStatus(orderId)).isEqualTo("EXPIRED") },
    { assertThat(reservationStatus(orderId)).isEqualTo("EXPIRED") },
    { assertThat(reservedQuantity(productId)).isZero() },
)
```

`PaymentCompletionRetryJobE2ETest` should seed `orders.status='FAILED'`, `payments.status='COMPLETION_FAILED'`, `stock_reservations.status='IN_PROGRESS'`, and matching `product_stocks.reserved_quantity`, then assert order becomes `COMPLETED`, payment `APPROVED`, reservation `COMPLETED`, actual stock is decremented, and reserved stock is zero.

- [ ] **Step 5: Run batch tests**

Run:

```bash
./gradlew :apps:commerce-batch:test --tests "com.loopers.job.order.OrderReservationExpirationJobE2ETest" --tests "com.loopers.job.order.PaymentCompletionRetryJobE2ETest"
```

Expected: PASS with Docker/Testcontainers available.

- [ ] **Step 6: Commit**

```bash
git add apps/commerce-batch/src/main/kotlin/com/loopers/batch/job/order apps/commerce-batch/src/test/kotlin/com/loopers/job/order
git commit -m "feat: add order recovery batch jobs"
```

---

## Task 15: Full Regression And Cleanup

**Files:**
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderCheckoutFacadeIntegrationTest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/AcceptanceV1ApiE2ETest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/catalog/ProductQueryFacadeTest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/shopping/CartFacadeTest.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1ApiSpec.kt`

- [ ] **Step 1: Search for old state names and order-owned payment transaction**

Run:

```bash
rg "ACTIVE|CONFIRMED|paymentTransactionId|completePayment\\(|cancelActive|restoreConfirmed|sumActiveQuantity|application.order.PaymentGateway" apps/commerce-api/src/main/kotlin apps/commerce-api/src/test/kotlin apps/commerce-batch/src/main/kotlin apps/commerce-batch/src/test/kotlin
```

Expected: no production references to old reservation states, order-owned payment transaction, old stock reservation sum model, or old payment gateway package. Test references should exist only when asserting legacy behavior was removed; prefer no references.

- [ ] **Step 2: Run commerce-api tests**

Run:

```bash
./gradlew :apps:commerce-api:test
```

Expected: PASS.

- [ ] **Step 3: Run commerce-batch tests**

Run:

```bash
./gradlew :apps:commerce-batch:test
```

Expected: PASS.

- [ ] **Step 4: Run lint**

Run:

```bash
./gradlew ktlintCheck
```

Expected: PASS. If it fails only with formatting violations, run:

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck
```

Expected after format: PASS.

- [ ] **Step 5: Run full build**

Run:

```bash
./gradlew build
```

Expected: PASS.

- [ ] **Step 6: Commit final cleanup**

```bash
git add apps/commerce-api apps/commerce-batch docs/order/plan_v2.md
git commit -m "test: update order v2 regression coverage"
```

---

## Self-Review

### Spec Coverage

- External PG calls outside DB transactions: Tasks 8, 10, 11 assert transaction boundary through `FakePaymentGateway.transactionActiveDuring*`.
- Atomic stock updates and affected row checks: Tasks 3, 4, 14.
- `payments` projection and append-only `payment_events`: Tasks 2, 6, 9, 10, 11, 14.
- No DB FK: Task 2 entities use scalar IDs only and no JPA relationships.
- Order, reservation, payment statuses: Tasks 1 and 2.
- `PgProvider.FAKE`: Task 2.
- Approve vs Verify command comments: Task 6.
- Save `payment_key` before approve call and append `APPROVE_REQUESTED`: Tasks 6 and 8.
- Completion failure state `order=FAILED`, `payment=COMPLETION_FAILED`, `reservation=IN_PROGRESS`: Task 8.
- FAILED retry with Verify and retry count < 3: Task 11.
- `stock_quantity` and `reserved_quantity`: Task 3.
- Reservation create/confirm/expire/cancel sequences: Tasks 4 and 9.
- Pre-payment cancel without PG cancel: Task 9.
- Post-payment cancel with PG cancel outside transaction: Task 10.
- User cancel forbidden for FAILED/EXPIRED/CANCELED/SHIPPING_STARTED: Tasks 1, 9, 10.
- Payment event append after state update in same transaction: Task 6 and event methods in Tasks 9-11.
- Batch expiration and retry: Task 14.
- API DTO detailed design excluded: Task 13 contains only compile/runtime minimum for `paymentKey`.

### Type Consistency

- `OrderCommand.Pay(orderId, paymentKey)` is used in Tasks 8, 11, and 13.
- Payment gateway package is consistently `com.loopers.application.payment`.
- Reservation states are consistently `IN_PROGRESS`, `COMPLETED`, `EXPIRED`, `CANCELED`.
- Payment retry counter is consistently `completionRetryCount` in Kotlin and `completion_retry_count` in SQL.
