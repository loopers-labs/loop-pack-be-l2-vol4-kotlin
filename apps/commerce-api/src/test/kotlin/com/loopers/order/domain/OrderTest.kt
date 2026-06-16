package com.loopers.order.domain

import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderTest {
    private fun snapshot(productId: Long = 1L, unitPrice: Long = 1000, quantity: Int = 1) =
        OrderItemSnapshot(
            productId = productId,
            brandId = 10L,
            productName = "에어맥스",
            brandName = "나이키",
            unitPrice = Money(unitPrice),
            quantity = quantity,
        )

    @DisplayName("주문을 생성하면, 기본 상태는 PENDING_PAYMENT다.")
    @Test
    fun defaultsToPendingPayment() {
        val order = Order.create(userId = 1L, snapshots = listOf(snapshot()))

        assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT)
    }

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenItemsEmpty() {
        val result = assertThrows<BadRequestException> {
            Order.create(userId = 1L, snapshots = emptyList())
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.EMPTY_ORDER_ITEMS)
    }

    @DisplayName("주문 항목 수량이 1 미만이면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenQuantityBelowOne() {
        val result = assertThrows<BadRequestException> {
            Order.create(userId = 1L, snapshots = listOf(snapshot(quantity = 0)))
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.INVALID_ORDER_QUANTITY)
    }

    @DisplayName("주문 총액은 항목별 단가×수량의 합으로 계산된다.")
    @Test
    fun calculatesTotalAmount() {
        val order = Order.create(
            userId = 1L,
            snapshots = listOf(
                snapshot(productId = 1L, unitPrice = 1000, quantity = 2),
                snapshot(productId = 2L, unitPrice = 500, quantity = 3),
            ),
        )

        assertAll(
            { assertThat(order.totalAmount).isEqualTo(Money(3500)) },
            { assertThat(order.items).hasSize(2) },
        )
    }

    @DisplayName("주문 항목 목록은 외부에서 수정할 수 없다.")
    @Test
    fun itemsAreUnmodifiable() {
        val order = Order.create(userId = 1L, snapshots = listOf(snapshot()))

        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (order.items as MutableList<OrderItem>).clear()
        }
    }

    @DisplayName("쿠폰 할인과 함께 생성하면, 금액 3종(원금·할인·최종)과 couponId가 박제된다.")
    @Test
    fun snapshotsAmountsWithDiscount() {
        val order = Order.create(
            userId = 1L,
            snapshots = listOf(snapshot(unitPrice = 10_000, quantity = 2)),
            couponId = 5L,
            discountAmount = Money(3_000),
        )

        assertAll(
            { assertThat(order.originalAmount).isEqualTo(Money(20_000)) },
            { assertThat(order.discountAmount).isEqualTo(Money(3_000)) },
            { assertThat(order.totalAmount).isEqualTo(Money(17_000)) },
            { assertThat(order.couponId).isEqualTo(5L) },
        )
    }

    @DisplayName("쿠폰 없이 생성하면, 할인은 0이고 최종 금액은 원금과 같다.")
    @Test
    fun snapshotsAmountsWithoutDiscount() {
        val order = Order.create(userId = 1L, snapshots = listOf(snapshot(unitPrice = 10_000, quantity = 2)))

        assertAll(
            { assertThat(order.discountAmount).isEqualTo(Money(0)) },
            { assertThat(order.totalAmount).isEqualTo(order.originalAmount) },
            { assertThat(order.couponId).isNull() },
        )
    }

    @DisplayName("PENDING_PAYMENT 주문은 결제 확정(PAID)·실패(FAILED)·불명(UNKNOWN)으로 전이할 수 있다.")
    @Test
    fun transitionsFromPendingPayment() {
        val paid = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        val failed = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        val unknown = Order.create(userId = 1L, snapshots = listOf(snapshot()))

        paid.confirmPayment()
        failed.failPayment()
        unknown.markUnknown()

        assertAll(
            { assertThat(paid.status).isEqualTo(OrderStatus.PAID) },
            { assertThat(failed.status).isEqualTo(OrderStatus.FAILED) },
            { assertThat(unknown.status).isEqualTo(OrderStatus.UNKNOWN) },
        )
    }

    @DisplayName("UNKNOWN 주문은 사후 확정으로 PAID 또는 FAILED로 전이할 수 있다.")
    @Test
    fun transitionsFromUnknown() {
        val toPaid = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        val toFailed = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        toPaid.markUnknown()
        toFailed.markUnknown()

        toPaid.confirmPayment()
        toFailed.failPayment()

        assertAll(
            { assertThat(toPaid.status).isEqualTo(OrderStatus.PAID) },
            { assertThat(toFailed.status).isEqualTo(OrderStatus.FAILED) },
        )
    }

    @DisplayName("종착 상태(PAID·FAILED)에서 전이를 시도하면, CONFLICT 예외가 발생한다.")
    @Test
    fun throwsConflict_whenTransitionFromTerminalStatus() {
        val paid = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        paid.confirmPayment()
        val failed = Order.create(userId = 1L, snapshots = listOf(snapshot()))
        failed.failPayment()

        assertAll(
            {
                val result = assertThrows<ConflictException> { paid.failPayment() }
                assertThat(result.errorCode).isEqualTo(OrderErrorCode.INVALID_STATUS_TRANSITION)
            },
            {
                val result = assertThrows<ConflictException> { paid.markUnknown() }
                assertThat(result.errorCode).isEqualTo(OrderErrorCode.INVALID_STATUS_TRANSITION)
            },
            {
                val result = assertThrows<ConflictException> { failed.confirmPayment() }
                assertThat(result.errorCode).isEqualTo(OrderErrorCode.INVALID_STATUS_TRANSITION)
            },
        )
    }
}
