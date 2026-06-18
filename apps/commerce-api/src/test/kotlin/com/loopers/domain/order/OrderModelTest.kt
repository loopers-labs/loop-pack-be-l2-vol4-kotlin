package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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

    @DisplayName("쿠폰을 적용할 때,")
    @Nested
    inner class ApplyCoupon {
        @DisplayName("할인 금액과 최종 결제 금액이 스냅샷으로 기록된다.")
        @Test
        fun recordsDiscountSnapshot() {
            // arrange
            val order = order()

            // act
            order.applyCoupon(userCouponId = 1L, discountAmount = BigDecimal("1000.00"))

            // assert
            assertAll(
                { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000.00")) },
                { assertThat(order.paidPrice).isEqualByComparingTo(BigDecimal("4000.00")) },
                { assertThat(order.userCouponId).isEqualTo(1L) },
            )
        }

        @DisplayName("할인 금액이 주문 금액을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDiscountExceedsTotal() {
            // arrange
            val order = order()

            // act
            val exception = assertThrows<CoreException> {
                order.applyCoupon(userCouponId = 1L, discountAmount = order.totalPrice + BigDecimal.ONE)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("쿠폰 미적용 주문의 최종 결제 금액은 주문 금액과 같다.")
        @Test
        fun paidPriceEqualsTotalPrice_withoutCoupon() {
            // arrange
            val order = order()

            // assert
            assertAll(
                { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal.ZERO) },
                { assertThat(order.paidPrice).isEqualByComparingTo(order.totalPrice) },
                { assertThat(order.userCouponId).isNull() },
            )
        }
    }

    private fun order(): OrderModel {
        return OrderModel(
            userId = 1L,
            items = listOf(
                orderItem(productId = 1L, price = "1000.00", quantity = 2),
                orderItem(productId = 2L, price = "3000.00", quantity = 1),
            ),
        )
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
