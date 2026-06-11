package com.loopers.order.domain

import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
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

    @DisplayName("주문을 생성하면, 기본 상태는 CREATED다.")
    @Test
    fun defaultsToCreated() {
        val order = Order.create(userId = 1L, snapshots = listOf(snapshot()))

        assertThat(order.status).isEqualTo(OrderStatus.CREATED)
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
}
