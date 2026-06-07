package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockTest {

    @DisplayName("재고 생성 시, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 productId와 0 이상의 수량이면 정상적으로 생성된다.")
        @Test
        fun create_whenAllFieldsAreValid() {
            // act
            val stock = Stock(productId = 1L, quantity = 10)

            // assert
            assertThat(stock.productId).isEqualTo(1L)
            assertThat(stock.quantity).isEqualTo(10)
        }

        @DisplayName("productId가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenProductIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> { Stock(productId = 0L, quantity = 10) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenQuantityIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> { Stock(productId = 1L, quantity = -1) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("재고 차감 가능 여부 검증 시, ")
    @Nested
    inner class ValidateDeductible {
        @DisplayName("재고가 차감 수량보다 많으면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockIsEnough() {
            // arrange
            val stock = Stock(productId = 1L, quantity = 10)

            // act & assert
            assertDoesNotThrow { stock.validateDeductible(3) }
        }

        @DisplayName("재고와 차감 수량이 같으면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockEqualsAmount() {
            // arrange
            val stock = Stock(productId = 1L, quantity = 3)

            // act & assert
            assertDoesNotThrow { stock.validateDeductible(3) }
        }

        @DisplayName("차감 수량이 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        fun throwsBadRequest_whenAmountIsNotPositive(amount: Int) {
            // arrange
            val stock = Stock(productId = 1L, quantity = 10)

            // act & assert
            val result = assertThrows<CoreException> { stock.validateDeductible(amount) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("재고가 차감 수량보다 적으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            val stock = Stock(productId = 1L, quantity = 2)

            // act & assert
            val result = assertThrows<CoreException> { stock.validateDeductible(3) }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("품절 여부 확인 시, ")
    @Nested
    inner class IsSoldOut {
        @DisplayName("수량이 0이면 품절이다.")
        @Test
        fun returnsTrue_whenQuantityIsZero() {
            // arrange
            val stock = Stock(productId = 1L, quantity = 0)

            // act & assert
            assertThat(stock.isSoldOut()).isTrue()
        }

        @DisplayName("수량이 1 이상이면 품절이 아니다.")
        @Test
        fun returnsFalse_whenQuantityIsPositive() {
            // arrange
            val stock = Stock(productId = 1L, quantity = 1)

            // act & assert
            assertThat(stock.isSoldOut()).isFalse()
        }
    }
}
