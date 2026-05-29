package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderAmountTest {
    @DisplayName("주문 금액 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상의 금액이면 정상적으로 생성된다.")
        @Test
        fun create_whenAmountIsZeroOrPositive() {
            // act
            val amount = OrderAmount(10_000L)

            // assert
            assertThat(amount.amount).isEqualTo(10_000L)
        }

        @DisplayName("금액이 0이면 ZERO와 동일하다.")
        @Test
        fun create_whenAmountIsZero() {
            // act
            val amount = OrderAmount(0L)

            // assert
            assertThat(amount).isEqualTo(OrderAmount.ZERO)
        }

        @DisplayName("금액이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenAmountIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { OrderAmount(-1L) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문 금액 덧셈 시, ")
    @Nested
    inner class Plus {
        @DisplayName("두 주문 금액을 더하면 합산된 주문 금액이 반환된다.")
        @Test
        fun plus_returnsSum() {
            // arrange
            val a = OrderAmount(10_000L)
            val b = OrderAmount(5_000L)

            // act
            val sum = a + b

            // assert
            assertThat(sum).isEqualTo(OrderAmount(15_000L))
        }
    }
}
