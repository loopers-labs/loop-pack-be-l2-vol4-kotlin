package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductPriceTest {
    @DisplayName("상품 가격 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("0 이상의 금액이면 정상적으로 생성된다.")
        @Test
        fun create_whenAmountIsZeroOrPositive() {
            // act
            val price = ProductPrice(10_000L)

            // assert
            assertThat(price.amount).isEqualTo(10_000L)
        }

        @DisplayName("금액이 0이면 정상적으로 생성된다.")
        @Test
        fun create_whenAmountIsZero() {
            // act
            val price = ProductPrice(0L)

            // assert
            assertThat(price.amount).isZero()
        }

        @DisplayName("금액이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenAmountIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { ProductPrice(-1L) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
