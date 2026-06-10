package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class OrderModelTest {
    @DisplayName("주문을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("여러 주문 항목의 총 금액을 계산하고 PENDING 상태로 생성한다.")
        @Test
        fun createsOrderWithTotalPrice() {
            // arrange
            val items = listOf(
                orderItem(productId = 1L, price = "1000.00", quantity = 2),
                orderItem(productId = 2L, price = "3000.00", quantity = 1),
            )

            // act
            val order = OrderModel(userId = 1L, items = items)

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PENDING)
            assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("5000.00"))
        }

        @DisplayName("주문 항목이 비어 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            // act
            val exception = assertThrows<CoreException> {
                OrderModel(userId = 1L, items = emptyList())
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun orderItem(productId: Long, price: String, quantity: Int): OrderItemModel {
        return OrderItemModel(
            productId = productId,
            productName = "Product$productId",
            price = BigDecimal(price),
            quantity = quantity,
        )
    }
}
