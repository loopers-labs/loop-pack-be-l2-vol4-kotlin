package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderTest {
    @DisplayName("주문 생성 시, ")
    @Nested
    inner class CreateOrder {
        @DisplayName("유저 ID와 주문 상품이 유효하면 결제 대기 상태의 주문이 생성된다.")
        @Test
        fun createOrder_whenAllFieldsAreValid() {
            // arrange
            val userId = 1L
            val items = listOf(
                newOrderItem(productId = 1L, productPrice = OrderItemPrice(10_000L), quantity = OrderQuantity(2)),
                newOrderItem(productId = 2L, productPrice = OrderItemPrice(5_000L), quantity = OrderQuantity(1)),
            )

            // act
            val order = Order(userId = userId, items = items)

            // assert
            assertAll(
                { assertThat(order.userId).isEqualTo(userId) },
                { assertThat(order.items).containsExactlyElementsOf(items) },
                { assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
                { assertThat(order.totalPrice).isEqualTo(OrderAmount(25_000L)) },
            )
        }

        @DisplayName("유저 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                Order(userId = 0L, items = listOf(newOrderItem()))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("주문 상품이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            // act & assert
            val result = assertThrows<CoreException> {
                Order(userId = 1L, items = emptyList())
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문 상태 변경 시, ")
    @Nested
    inner class ChangeStatus {
        @DisplayName("주문을 결제 완료 상태로 변경할 수 있다.")
        @Test
        fun markPaid() {
            // arrange
            val order = Order(userId = 1L, items = listOf(newOrderItem()))

            // act
            order.markPaid()

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("결제 완료 상태의 주문은 결제 실패 상태로 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenMarkPaymentFailedAfterPaid() {
            // arrange
            val order = Order(userId = 1L, items = listOf(newOrderItem()))
            order.markPaid()

            // act & assert
            val result = assertThrows<CoreException> {
                order.markPaymentFailed()
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("주문을 결제 실패 상태로 변경할 수 있다.")
        @Test
        fun markPaymentFailed() {
            // arrange
            val order = Order(userId = 1L, items = listOf(newOrderItem()))

            // act
            order.markPaymentFailed()

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }

        @DisplayName("결제 실패 상태의 주문은 결제 완료 상태로 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenMarkPaidAfterPaymentFailed() {
            // arrange
            val order = Order(userId = 1L, items = listOf(newOrderItem()))
            order.markPaymentFailed()

            // act & assert
            val result = assertThrows<CoreException> {
                order.markPaid()
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun newOrderItem(
        productId: Long = 1L,
        productName: String = "Loopers T-Shirt",
        productPrice: OrderItemPrice = OrderItemPrice(10_000L),
        quantity: OrderQuantity = OrderQuantity(1),
    ) = OrderItem(
        productSnapshot = ProductSnapshot(
            productId = productId,
            productName = productName,
            productPrice = productPrice,
        ),
        quantity = quantity,
    )
}
