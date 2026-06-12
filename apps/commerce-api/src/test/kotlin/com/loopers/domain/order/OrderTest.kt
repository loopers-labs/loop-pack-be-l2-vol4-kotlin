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
            val order = newOrder(userId = userId, items = items)

            // assert
            assertAll(
                { assertThat(order.userId).isEqualTo(userId) },
                { assertThat(order.userCouponId).isNull() },
                { assertThat(order.items).containsExactlyElementsOf(items) },
                { assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
                { assertThat(order.totalAmount).isEqualTo(OrderAmount(25_000L)) },
                { assertThat(order.discountAmount).isEqualTo(OrderAmount.ZERO) },
                { assertThat(order.paymentAmount).isEqualTo(OrderAmount(25_000L)) },
            )
        }

        @DisplayName("금액 정보가 주어지면 해당 금액을 주문 상태로 보존한다.")
        @Test
        fun createOrder_whenAmountsAreProvided() {
            // arrange
            val items = listOf(
                newOrderItem(productId = 1L, productPrice = OrderItemPrice(10_000L), quantity = OrderQuantity(2)),
            )
            val amounts = OrderAmounts.of(
                totalAmount = OrderAmount(20_000L),
                discountAmount = OrderAmount(1_000L),
            )

            // act
            val order = newOrder(userId = 1L, items = items, amounts = amounts)

            // assert
            assertAll(
                { assertThat(order.totalAmount).isEqualTo(OrderAmount(20_000L)) },
                { assertThat(order.discountAmount).isEqualTo(OrderAmount(1_000L)) },
                { assertThat(order.paymentAmount).isEqualTo(OrderAmount(19_000L)) },
            )
        }

        @DisplayName("발급 쿠폰 ID가 주어지면 주문 상태로 보존한다.")
        @Test
        fun createOrder_whenUserCouponIdIsProvided() {
            // act
            val order = newOrder(userCouponId = 10L)

            // assert
            assertThat(order.userCouponId).isEqualTo(10L)
        }

        @DisplayName("발급 쿠폰 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserCouponIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                newOrder(userCouponId = 0L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("유저 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenUserIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                newOrder(userId = 0L, items = listOf(newOrderItem()))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("주문 상품이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            // act & assert
            val result = assertThrows<CoreException> {
                newOrder(userId = 1L, items = emptyList())
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
            val order = newOrder()

            // act
            order.markPaid()

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("결제 완료 상태의 주문은 결제 실패 상태로 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenMarkPaymentFailedAfterPaid() {
            // arrange
            val order = newOrder()
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
            val order = newOrder()

            // act
            order.markPaymentFailed()

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }

        @DisplayName("결제 실패 상태의 주문은 결제 완료 상태로 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenMarkPaidAfterPaymentFailed() {
            // arrange
            val order = newOrder()
            order.markPaymentFailed()

            // act & assert
            val result = assertThrows<CoreException> {
                order.markPaid()
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("주문을 취소 상태로 변경할 수 있다.")
        @Test
        fun cancel() {
            // arrange
            val order = newOrder()

            // act
            order.cancel()

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }

        @DisplayName("결제 완료 상태의 주문은 취소 상태로 변경할 수 없다.")
        @Test
        fun throwsBadRequest_whenCancelAfterPaid() {
            // arrange
            val order = newOrder()
            order.markPaid()

            // act & assert
            val result = assertThrows<CoreException> {
                order.cancel()
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

    private fun newOrder(
        userId: Long = 1L,
        userCouponId: Long? = null,
        items: List<OrderItem> = listOf(newOrderItem()),
        amounts: OrderAmounts = OrderAmountCalculator.calculate(items),
    ) = Order(
        userId = userId,
        userCouponId = userCouponId,
        items = items,
        amounts = amounts,
    )
}
