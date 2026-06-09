package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DiscountAmountTest {

    @DisplayName("할인 금액 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상이면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(longs = [0L, 1L, 10_000L])
        fun create_whenNonNegative(amount: Long) {
            // act
            val discountAmount = DiscountAmount(amount)

            // assert
            assertThat(discountAmount.amount).isEqualTo(amount)
        }

        @DisplayName("음수이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [-1L, -1_000L])
        fun throwsBadRequest_whenNegative(amount: Long) {
            // act & assert
            val result = assertThrows<CoreException> { DiscountAmount(amount) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("ZERO 상수는 0 금액을 의미한다.")
    @Test
    fun zeroConstant() {
        // assert
        assertThat(DiscountAmount.ZERO).isEqualTo(DiscountAmount(0L))
    }
}
