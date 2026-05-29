package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderQuantityTest {
    @DisplayName("주문 수량 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("1 이상의 수량이면 정상적으로 생성된다.")
        @Test
        fun create_whenValueIsPositive() {
            // act
            val quantity = OrderQuantity(3)

            // assert
            assertThat(quantity.value).isEqualTo(3)
        }

        @DisplayName("수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenValueIsZero() {
            // act & assert
            val result = assertThrows<CoreException> { OrderQuantity(0) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenValueIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { OrderQuantity(-1) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
