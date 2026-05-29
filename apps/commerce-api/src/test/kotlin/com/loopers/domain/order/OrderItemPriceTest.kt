package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderItemPriceTest {
    @DisplayName("주문 상품 가격 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상의 금액이면 정상적으로 생성된다.")
        @Test
        fun create_whenAmountIsZeroOrPositive() {
            // act
            val price = OrderItemPrice(10_000L)

            // assert
            assertThat(price.amount).isEqualTo(10_000L)
        }

        @DisplayName("금액이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenAmountIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { OrderItemPrice(-1L) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문 상품 가격과 수량 곱셈 시, ")
    @Nested
    inner class Times {
        @DisplayName("가격에 수량을 곱하면 주문 금액이 반환된다.")
        @Test
        fun times_returnsOrderAmount() {
            // arrange
            val price = OrderItemPrice(10_000L)
            val quantity = OrderQuantity(3)

            // act
            val total = price * quantity

            // assert
            assertThat(total).isEqualTo(OrderAmount(30_000L))
        }
    }
}
