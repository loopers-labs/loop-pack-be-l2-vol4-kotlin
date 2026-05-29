package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class OrderItemTest {
    @DisplayName("주문 상품 생성 시, ")
    @Nested
    inner class CreateOrderItem {
        @DisplayName("주문 당시 상품 정보와 수량이 유효하면 스냅샷이 생성된다.")
        @Test
        fun createOrderItem_whenAllFieldsAreValid() {
            // arrange
            val productId = 1L
            val productName = "Loopers T-Shirt"
            val productPrice = OrderItemPrice(10_000L)
            val productSnapshot = ProductSnapshot(
                productId = productId,
                productName = productName,
                productPrice = productPrice,
            )
            val quantity = OrderQuantity(3)

            // act
            val orderItem = OrderItem(
                productSnapshot = productSnapshot,
                quantity = quantity,
            )

            // assert
            assertAll(
                { assertThat(orderItem.productSnapshot).isEqualTo(productSnapshot) },
                { assertThat(orderItem.productId).isEqualTo(productId) },
                { assertThat(orderItem.productName).isEqualTo(productName) },
                { assertThat(orderItem.productPrice).isEqualTo(productPrice) },
                { assertThat(orderItem.quantity).isEqualTo(quantity) },
                { assertThat(orderItem.totalPrice).isEqualTo(OrderAmount(30_000L)) },
            )
        }

        @DisplayName("상품 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                newOrderItemWith(productId = 0L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductNameIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                newOrderItemWith(productName = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    private fun newOrderItemWith(
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
