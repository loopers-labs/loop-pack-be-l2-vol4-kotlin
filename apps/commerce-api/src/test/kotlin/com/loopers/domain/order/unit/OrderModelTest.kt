package com.loopers.domain.order.unit

import com.loopers.domain.order.exception.InvalidOrderException
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_도메인_생성
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderModelTest {
    @Test
    fun `주문은_하나_이상의_상품을_포함하고_총액을_계산한다`() {
        val order = OrderModel.create(
            orderedUserId = 1L,
            items = listOf(
                주문항목_도메인_생성(productId = 10L, quantity = 2, unitPrice = 10_000),
                주문항목_도메인_생성(productId = 20L, quantity = 1, unitPrice = 5_000),
            ),
        )

        assertThat(order.items).hasSize(2)
        assertThat(order.totalPrice.value).isEqualTo(25_000)
        assertThat(order.discountPrice.value).isZero()
        assertThat(order.paymentPrice.value).isEqualTo(25_000)
    }

    @Test
    fun `주문은_주문항목이_필수다`() {
        assertThrows<InvalidOrderException> {
            OrderModel.create(orderedUserId = 1L, items = emptyList())
        }
    }

    @Test
    fun `주문자는_양수여야_한다`() {
        assertThrows<InvalidOrderException> {
            OrderModel.create(
                orderedUserId = 0L,
                items = listOf(주문항목_도메인_생성()),
            )
        }
    }

    @Test
    fun `저장된_주문_ID는_양수여야_한다`() {
        assertThrows<InvalidOrderException> {
            OrderModel.fromPersisted(
                id = 0L,
                orderedUserId = 1L,
                items = listOf(주문항목_도메인_생성(orderId = 0L)),
                totalPrice = 20_000L,
                discountPrice = 0L,
                paymentPrice = 20_000L,
            )
        }
    }

    @Test
    fun `주문은_본인_소유인지_판별한다`() {
        val order = OrderModel.create(
            orderedUserId = 1L,
            items = listOf(주문항목_도메인_생성()),
        )

        assertThat(order.belongsTo(1L)).isTrue()
        assertThat(order.belongsTo(2L)).isFalse()
    }
}
